package com.gnojes.mockpin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gnojes.mockpin.data.SavedLocation

@Composable
internal fun CoordinateDialog(selected: SavedLocation, onDismiss: () -> Unit, onApply: (SavedLocation) -> Unit) {
    var latitude by rememberSaveable { mutableStateOf(selected.latitude.toString()) }
    var longitude by rememberSaveable { mutableStateOf(selected.longitude.toString()) }
    val parsed = SavedLocation.parseCoordinates(latitude, longitude)
    val latValid = latitude.toDoubleOrNull()?.let { it.isFinite() && it in -90.0..90.0 } == true
    val lonValid = longitude.toDoubleOrNull()?.let { it.isFinite() && it in -180.0..180.0 } == true
    AlertDialog(onDismissRequest = onDismiss, title = { Text("좌표 직접 입력") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude") }, supportingText = { Text("-90 ~ 90") },
                isError = !latValid, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude") }, supportingText = { Text("-180 ~ 180") },
                isError = !lonValid, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            if (parsed == null) Text("유효한 위도와 경도를 입력해 주세요.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { parsed?.let(onApply) }, enabled = parsed != null) { Text("적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable
internal fun FavoriteDialog(selected: SavedLocation, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(selected.name) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("즐겨찾기 저장") }, text = {
        Column { Text(selected.coordinates); OutlinedTextField(name, { name = it.take(100) }, label = { Text("표시할 이름") }, singleLine = true) }
    }, confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable
internal fun SavedLocationsDialog(title: String, locations: List<SavedLocation>, onDismiss: () -> Unit,
    onSelect: (SavedLocation) -> Unit, onDelete: ((SavedLocation) -> Unit)?) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        if (locations.isEmpty()) Text("저장된 위치가 없습니다.")
        else LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(locations) { point ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).clickable { onSelect(point) }.padding(vertical = 12.dp)) {
                        Text(point.name, style = MaterialTheme.typography.titleSmall)
                        Text(point.coordinates, style = MaterialTheme.typography.bodySmall)
                        point.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    onDelete?.let { TextButton(onClick = { it(point) }) { Text("삭제") } }
                }
                HorizontalDivider()
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}
