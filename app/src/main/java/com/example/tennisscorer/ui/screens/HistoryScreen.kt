package com.example.tennisscorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tennisscorer.data.MatchRecord
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.ui.components.MatchHistoryCard
import com.example.tennisscorer.ui.theme.AppBg
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.viewmodels.HistoryViewModel

@Composable
fun HistoryScreen(
    repository: MatchRepository,
    onMatchClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(repository))
    val matches by vm.matches.collectAsState()
    var deleteTarget by remember { mutableStateOf<MatchRecord?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(AppBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Riwayat Pertandingan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (matches.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎾", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Belum ada pertandingan",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(matches, key = { it.matchId }) { record ->
                        MatchHistoryCard(
                            record = record,
                            onDelete = { deleteTarget = record },
                            modifier = Modifier.clickable { onMatchClick(record.matchId) }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus Pertandingan?", color = Color.White) },
            text = {
                Text(
                    "${record.p1Name} vs ${record.p2Name} akan dihapus permanen.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteMatch(record.matchId); deleteTarget = null }) {
                    Text("Hapus", color = CyanAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Batal", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}
