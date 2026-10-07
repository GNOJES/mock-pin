package com.gnojes.mockpin.location

import kotlinx.coroutines.Job

// All START/STOP/UPDATE/CLEANUP commands use the same driver-ownership gate.
internal val Job?.isInFlight: Boolean get() = this != null && !isCompleted
