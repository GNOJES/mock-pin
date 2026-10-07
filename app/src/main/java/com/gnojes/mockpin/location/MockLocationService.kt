package com.gnojes.mockpin.location

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.gnojes.mockpin.MainActivity
import com.gnojes.mockpin.R
import com.gnojes.mockpin.data.LocationStore
import com.gnojes.mockpin.data.RecentRecorder
import com.gnojes.mockpin.data.SavedLocation
import kotlinx.coroutines.*

class MockLocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var driver: MockDriver
    private var job: Job? = null
    private var target = SavedLocation.DEFAULT
    private lateinit var recentRecorder: RecentRecorder
    private var storageWarning: UserError? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        driver = FusedMockDriver(this)
        recentRecorder = RecentRecorder { LocationStore(this).recordRecent(it) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Mock location", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> stopMock()
            CLEANUP -> if (!job.isInFlight) startCleanup()
            START, UPDATE -> {
                val point = intent.point() ?: run {
                    MockServiceState.publish(MockStatus(error = UserError("잘못된 좌표입니다.")))
                    stopSelf(); return START_NOT_STICKY
                }
                if (job.isInFlight) {
                    if (MockServiceState.status.value.phase == MockPhase.ACTIVE) target = point
                } else startMock(point)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun promote(point: SavedLocation) {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(point), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun startMock(point: SavedLocation) {
        val setupError = MockSetup.check(this)
        if (setupError != null) {
            MockServiceState.publish(MockStatus(error = setupError))
            stopSelf(); return
        }
        try { promote(point) }
        catch (error: Exception) {
            MockServiceState.publish(MockStatus(error = UserError("Foreground Service를 시작할 수 없습니다. 앱 화면에서 다시 START를 눌러 주세요.", HelpAction.APP_SETTINGS)))
            stopSelf(); return
        }
        target = point
        MockServiceState.publish(MockStatus(MockPhase.STARTING))
        // Durable uncertainty marker: a process kill cannot execute finally. Never auto-start.
        setPendingCleanup(true)
        job = scope.launch {
            var failure: UserError? = null
            var cleanupFailed = false
            try {
                acquireWakeLock()
                MockSession(driver, SampleClock(System::currentTimeMillis, SystemClock::elapsedRealtimeNanos)).run({ target }) { applied ->
                    MockServiceState.publish(MockStatus(MockPhase.ACTIVE, applied, storageWarning))
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(applied))
                    try {
                        recentRecorder.recordIfChanged(applied)
                        storageWarning = null
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        storageWarning = UserError("최근 위치를 저장하지 못했습니다. 저장 공간을 확인해 주세요. 다음 업데이트에서 다시 시도합니다.")
                    }
                    currentCoroutineContext().ensureActive()
                    MockServiceState.publish(MockStatus(MockPhase.ACTIVE, applied, storageWarning))
                }
            } catch (error: MockCleanupException) {
                cleanupFailed = true
                failure = UserError("Mock Mode 정리를 확인하지 못했습니다. 위치 권한과 모의 위치 앱 설정을 확인하고 ‘Mock Mode 정리’를 눌러 주세요.", HelpAction.DEVELOPER_SETTINGS)
            } catch (_: CancellationException) {
                // STOP or service destruction; MockSession already awaited cleanup.
            } catch (error: Exception) { failure = MockSetup.error(error) }
            finally { finish(cleanupFailed, failure) }
        }
    }

    private fun stopMock() {
        if (job.isInFlight) {
            MockServiceState.publish(MockServiceState.status.value.copy(phase = MockPhase.STOPPING))
            job?.cancel()
        } else if (hasPendingCleanup(this)) startCleanup() else finish(false, null)
    }

    private fun startCleanup() {
        try { promote(MockServiceState.status.value.current ?: target) }
        catch (_: Exception) {
            MockServiceState.publish(MockStatus(MockPhase.RECOVERY_REQUIRED, error = UserError("정리를 시작할 수 없습니다. 위치 권한 및 위치 서비스를 확인해 주세요.", HelpAction.APP_SETTINGS)))
            stopSelf(); return
        }
        MockServiceState.publish(MockStatus(MockPhase.STOPPING))
        job = scope.launch {
            var failed = false
            try { withContext(NonCancellable) { withTimeout(10_000) { driver.disable() } } }
            catch (_: Exception) { failed = true }
            finally { finish(failed, if (failed) UserError("Mock Mode 정리에 실패했습니다. 모의 위치 앱 설정과 위치 권한을 확인한 뒤 재시도해 주세요.", HelpAction.DEVELOPER_SETTINGS) else null) }
        }
    }

    private fun finish(cleanupFailed: Boolean, error: UserError?) {
        releaseWakeLock()
        setPendingCleanup(cleanupFailed)
        MockServiceState.publish(MockStatus(if (cleanupFailed) MockPhase.RECOVERY_REQUIRED else MockPhase.INACTIVE, error = error))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @android.annotation.SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        // The user explicitly starts a continuous 1-second foreground operation.
        // The lock is scoped to the session and released in finish AND onDestroy.
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MockPin:MockLocation").apply {
            setReferenceCounted(false)
            acquire()
        }
    }
    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun setPendingCleanup(value: Boolean) {
        getSharedPreferences("mock_lifecycle", MODE_PRIVATE).edit().putBoolean("pending_cleanup", value).commit()
    }

    private fun notification(point: SavedLocation): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, MockLocationService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mock_pin)
            .setContentTitle("Mock location active")
            .setContentText("${point.name} · ${point.coordinates}")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "STOP", stop).build()
    }

    override fun onDestroy() {
        releaseWakeLock()
        if (job.isInFlight) {
            MockServiceState.publish(MockServiceState.status.value.copy(phase = MockPhase.STOPPING))
        }
        scope.cancel() // MockSession finally uses NonCancellable to complete disable.
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "mock_location"
        private const val NOTIFICATION_ID = 1
        const val START = "com.gnojes.mockpin.START"
        const val STOP = "com.gnojes.mockpin.STOP"
        const val UPDATE = "com.gnojes.mockpin.UPDATE"
        const val CLEANUP = "com.gnojes.mockpin.CLEANUP"
        fun hasPendingCleanup(context: Context): Boolean = context.getSharedPreferences("mock_lifecycle", Context.MODE_PRIVATE).getBoolean("pending_cleanup", false)
        fun command(context: Context, action: String, point: SavedLocation? = null): Intent =
            Intent(context, MockLocationService::class.java).setAction(action).apply {
                point?.let {
                    putExtra("name", it.name); putExtra("latitude", it.latitude); putExtra("longitude", it.longitude)
                    putExtra("address", it.address); putExtra("placeId", it.placeId)
                }
            }
        private fun Intent.point(): SavedLocation? {
            if (!hasExtra("latitude") || !hasExtra("longitude")) return null
            return SavedLocation(getStringExtra("name") ?: "선택 위치", getDoubleExtra("latitude", Double.NaN),
                getDoubleExtra("longitude", Double.NaN), getStringExtra("address"), getStringExtra("placeId")).takeIf { it.valid }
        }
    }
}
