package com.gnojes.mockpin.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gnojes.mockpin.MockPinViewModel
import com.gnojes.mockpin.data.SavedLocation
import com.gnojes.mockpin.location.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

@Composable
fun MockPinScreen(model: MockPinViewModel, onStart: () -> Unit, onStop: () -> Unit, onApply: () -> Unit,
    onCleanup: () -> Unit, onSettings: (HelpAction) -> Unit) {
    val selected by model.selected.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    val search by model.search.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val recent by model.recent.collectAsStateWithLifecycle()
    val userError by model.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus(); keyboard?.hide() }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var keyHelp by rememberSaveable { mutableStateOf(false) }
    val showHelp: (HelpAction) -> Unit = { if (it == HelpAction.API_KEY) keyHelp = true else onSettings(it) }
    val playServicesAvailable = remember { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS }

    BoxWithConstraints(Modifier.fillMaxSize().focusRequester(initialFocus).focusable()) {
        val panelMaxHeight = maxHeight * 0.48f
        Scaffold(bottomBar = {
            Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
                Column(Modifier.heightIn(max = panelMaxHeight).verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val phaseText = when (status.phase) {
                        MockPhase.ACTIVE -> "Mock Location ACTIVE"
                        MockPhase.STARTING -> "Mock Location 시작 중…"
                        MockPhase.STOPPING -> "Mock Mode 정리 중…"
                        MockPhase.RECOVERY_REQUIRED -> "Mock Mode 정리 필요"
                        MockPhase.INACTIVE -> "Mock Location INACTIVE"
                    }
                    Text(phaseText, fontWeight = FontWeight.Bold,
                        color = if (status.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    status.current?.takeIf { status.running || status.phase == MockPhase.STOPPING }?.let {
                        Text("적용 중: ${it.name} · ${it.coordinates}", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    selected.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text("선택: ${selected.coordinates}", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (status.phase) {
                            MockPhase.ACTIVE, MockPhase.STARTING -> {
                                Button(onClick = onStop, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("STOP") }
                                if (status.phase == MockPhase.ACTIVE && status.current != selected)
                                    Button(onClick = onApply, modifier = Modifier.weight(1f)) { Text("선택 위치 적용") }
                            }
                            MockPhase.STOPPING -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("정리 중…") }
                            MockPhase.RECOVERY_REQUIRED -> Button(onClick = onCleanup, modifier = Modifier.fillMaxWidth()) { Text("Mock Mode 정리") }
                            MockPhase.INACTIVE -> Button(onClick = onStart, enabled = selected.valid, modifier = Modifier.fillMaxWidth()) { Text("START") }
                        }
                    }
                    (userError ?: status.error)?.let { ErrorCard(it, showHelp) }
                    if (status.running && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        Text("알림 권한을 허용하면 알림에서 STOP할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { dialog = "save" }) { Text("★ 저장") }
                        TextButton(onClick = { dialog = "coordinates" }) { Text("좌표") }
                        TextButton(onClick = { dialog = "favorites" }) { Text("즐겨찾기") }
                        TextButton(onClick = { dialog = "recent" }) { Text("최근") }
                    }
                }
            }
        }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                if (model.keyConfigured && playServicesAvailable) LocationMap(selected, status.current?.takeIf { status.running || status.phase == MockPhase.STOPPING },
                    onSelect = { focusManager.clearFocus(); keyboard?.hide(); model.select(it) },
                    onLocate = { focusManager.clearFocus(); keyboard?.hide() },
                    onError = model::report, onKeyHelp = { keyHelp = true })
                else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MockPin", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(if (!model.keyConfigured) "지도를 사용하려면 API Key를 설정해 주세요. 좌표 입력과 저장 위치로 모의 위치를 사용할 수 있습니다."
                        else "Google Play services를 설치하거나 업데이트해야 지도를 사용할 수 있습니다.")
                    TextButton(onClick = { keyHelp = true }) { Text("API 설정 안내") }
                }
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = MaterialTheme.shapes.large, shadowElevation = 6.dp) {
                        OutlinedTextField(value = search.query, onValueChange = { model.queryChanged(it) },
                            placeholder = { Text("장소명 / 주소 검색 (2자 이상)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), trailingIcon = {
                                if (search.query.isNotEmpty()) TextButton(onClick = { model.queryChanged("") }) { Text("지우기") }
                            })
                    }
                    if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    search.error?.let {
                        Surface(shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.padding(8.dp)) { ErrorCard(it, showHelp); TextButton(onClick = model::retrySearch) { Text("다시 시도") } }
                        }
                    }
                    if (search.results.isNotEmpty()) Surface(shape = MaterialTheme.shapes.medium, shadowElevation = 4.dp) {
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(search.results, key = { it.id }) { prediction ->
                                Column(Modifier.fillMaxWidth().clickable { keyboard?.hide(); model.chooseSuggestion(prediction) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(prediction.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(prediction.address, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    if (search.searched && search.results.isEmpty() && !search.loading && search.error == null)
                        Surface(shape = MaterialTheme.shapes.medium) { Text("검색 결과가 없습니다. 다른 이름이나 주소로 검색해 주세요.", Modifier.padding(12.dp)) }
                }
            }
        }
    }
    when (dialog) {
        "coordinates" -> CoordinateDialog(selected, { dialog = null }) { keyboard?.hide(); model.select(it); dialog = null }
        "save" -> FavoriteDialog(selected, { dialog = null }) { model.saveFavorite(it); dialog = null }
        "favorites", "recent" -> SavedLocationsDialog(if (dialog == "favorites") "즐겨찾기" else "최근 사용 위치",
            if (dialog == "favorites") favorites else recent, onDismiss = { dialog = null },
            onSelect = { model.select(it); dialog = null },
            onDelete = if (dialog == "favorites") { point -> model.removeFavorite(point); Unit } else null)
    }
    if (keyHelp) AlertDialog(onDismissRequest = { keyHelp = false }, title = { Text("Google Maps / Places 설정") }, text = {
        Text("프로젝트 루트의 secrets.properties에 MAPS_API_KEY를 입력하고 다시 빌드하세요.\n\nGoogle Cloud에서 Maps SDK for Android와 Places API (New)를 활성화하고 결제·quota를 확인하세요. 키의 Android 제한은 com.gnojes.mockpin과 서명 SHA-1을 사용합니다.\n\n지도 인증 오류는 SDK가 화면에 전달하지 않을 수 있습니다. 지도가 비어 있으면 API 설정과 인터넷 연결을 확인하세요.")
    }, confirmButton = { TextButton(onClick = { keyHelp = false }) { Text("확인") } })
}

@Composable
private fun ErrorCard(error: UserError, onHelp: (HelpAction) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small).padding(10.dp)) {
        Text(error.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
        error.action?.let { action ->
            TextButton(onClick = { onHelp(action) }, contentPadding = PaddingValues(0.dp)) {
                Text(when (action) {
                    HelpAction.DEVELOPER_SETTINGS -> "개발자 옵션 열기"
                    HelpAction.LOCATION_SETTINGS -> "위치 설정 열기"
                    HelpAction.APP_SETTINGS -> "앱 권한 설정 열기"
                    HelpAction.API_KEY -> "API 설정 안내"
                })
            }
        }
    }
}
