package YANDIKA.PACKAGE.YAWE

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatInputBar(onSend: (text: String, image: Uri?, file: Uri?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Uri?>(null) }
    var file by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) image = uri }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) file = uri }

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        if (image != null || file != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (image != null) "📷 Ifoto yatoranijwe" else "📄 Dosiye yatoranijwe",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { image = null; file = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Kuramwo")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) { Icon(Icons.Default.Image, contentDescription = "Ifoto") }

            IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.AttachFile, contentDescription = "Dosiye")
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Type here...") },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {
                if (text.isNotBlank() || image != null || file != null) {
                    onSend(text, image, file)
                    text = ""; image = null; file = null
                }
            }) { Icon(Icons.Default.Send, contentDescription = "Kohereza") }
        }
    }
}
