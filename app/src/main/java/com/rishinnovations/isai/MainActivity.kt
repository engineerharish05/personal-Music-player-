package com.rishinnovations.isai

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

private val Ink = Color(0xFF090B0E)
private val Panel = Color(0xFF171D23)
private val Gold = Color(0xFFE5C46A)
private val SoftText = Color(0xFFE5E7EB)

data class LocalTrack(val id: Long, val title: String, val artist: String, val uri: String)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try { controller = controllerFuture?.get() } catch (_: Exception) { controller = null }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Ink) {
                    ISAIHome(
                        getController = { controller },
                        scanLibrary = { loadLocalTracks() }
                    )
                }
            }
        }
    }

    private fun loadLocalTracks(): List<LocalTrack> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val result = mutableListOf<LocalTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        contentResolver.query(
            collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                result += LocalTrack(id, cursor.getString(titleCol) ?: "Unknown title",
                    cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown artist", uri)
            }
        }
        return result
    }
}

@Composable
private fun ISAIHome(getController: () -> MediaController?, scanLibrary: () -> List<LocalTrack>) {
    var tracks by remember { mutableStateOf(emptyList<LocalTrack>()) }
    var permissionGranted by remember { mutableStateOf(false) }
    var nowPlaying by remember { mutableStateOf("Choose a track") }
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Your music, in one place.") }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) tracks = scanLibrary()
        else message = "Audio permission is needed to scan local music."
    }
    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(
            androidx.compose.ui.platform.LocalContext.current, permission
        ) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) tracks = scanLibrary()
    }

    Column(
        Modifier.fillMaxSize().background(Ink).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("ISAI", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("MUSIC / PLAYER", color = SoftText.copy(alpha = .65f), fontSize = 11.sp, letterSpacing = 2.sp)
            }
            Text("●  READY", color = Gold, fontSize = 11.sp)
        }
        Text(message, color = SoftText.copy(alpha = .8f), fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (permissionGranted) tracks = scanLibrary() else requestPermission.launch(permission)
            }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                Text("Scan device")
            }
            Button(onClick = {
                val controller = getController()
                if (controller?.isPlaying == true) controller.pause() else controller?.play()
            }, colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = SoftText)) {
                Text("Play / Pause")
            }
        }
        Text("ONLINE STREAM", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        OutlinedTextField(
            value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("Direct audio URL", color = SoftText.copy(alpha = .65f)) },
            placeholder = { Text("https://…/audio.mp3", color = SoftText.copy(alpha = .4f)) }
        )
        Button(onClick = {
            val controller = getController()
            if (url.startsWith("https://") && controller != null) {
                controller.setMediaItem(MediaItem.Builder().setUri(url)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("Online stream").setArtist("ISAI stream").build()).build())
                controller.prepare()
                controller.play()
                nowPlaying = "Online stream"
                message = "Streaming direct audio URL."
            } else message = "Enter a valid HTTPS audio URL and wait for the player to connect."
        }, colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)) {
            Text("Play URL")
        }
        Text("YOUR LIBRARY  ·  ${tracks.size} TRACKS", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        if (tracks.isEmpty()) {
            Text("No tracks loaded yet. Tap Scan device to grant access and find audio files.", color = SoftText.copy(alpha = .65f), fontSize = 13.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(tracks, key = { it.id }) { track ->
                    Row(
                        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp))
                            .clickable {
                                val controller = getController() ?: return@clickable
                                val items = tracks.map { t ->
                                    MediaItem.Builder().setUri(t.uri).setMediaId(t.id.toString())
                                        .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).build()).build()
                                }
                                val index = tracks.indexOf(track)
                                controller.setMediaItems(items, index, 0L)
                                controller.prepare()
                                controller.play()
                                nowPlaying = track.title
                                message = "Playing from your device."
                            }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = SoftText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(track.artist, color = SoftText.copy(alpha = .6f), fontSize = 12.sp, maxLines = 1)
                        }
                        Text("▶", color = Gold)
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("NOW PLAYING", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(getController()?.currentMediaItem?.mediaMetadata?.title?.toString() ?: nowPlaying,
                    color = SoftText, fontSize = 14.sp, maxLines = 1)
            }
            Button(onClick = { getController()?.seekToPreviousMediaItem() },
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Gold)) { Text("|◀") }
            Button(onClick = {
                val c = getController()
                if (c?.isPlaying == true) c.pause() else c?.play()
            }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("▶ / Ⅱ") }
            Button(onClick = { getController()?.seekToNextMediaItem() },
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Gold)) { Text("▶|") }
        }
    }
}
