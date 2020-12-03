package com.vitalware.succ


import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat.finishAffinity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.coremedia.iso.boxes.Container
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.firebase.storage.ktx.storageMetadata
import com.googlecode.mp4parser.authoring.Movie
import com.googlecode.mp4parser.authoring.Track
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import com.googlecode.mp4parser.authoring.tracks.AppendTrack
import com.vitalware.succ.databinding.FragmentRecordBinding
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.Runnable
import java.nio.channels.FileChannel
import java.util.*
import kotlin.collections.ArrayList


/**
 * A simple [Fragment] subclass.
 */
@Suppress("SameParameterValue", "BlockingMethodInNonBlockingContext")
class RecordFragment : Fragment() {
    private val storageRef = Firebase.storage.getReference("audios")
    private lateinit var binding: FragmentRecordBinding
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var fileName: String
    private lateinit var mediaPlayer: MediaPlayer
    private var isComplete = true
    private var isPaused = false
    private var isRecording = false
    private lateinit var durationMin: String
    private val audioList = mutableListOf<String>()
    private lateinit var finalFileName: String
    private var timeWhenStopped: Long = 0
    private var mergeJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + mergeJob)
    private val audioData = mutableMapOf<String, Boolean>()
    private var audioRecords: DatabaseReference = Firebase.database.getReference("audios")
    private lateinit var args: RecordFragmentArgs
    private var connected = false
    private val metadata = storageMetadata {
        contentType = "audio/mp3"
    }
    private lateinit var root: File
    @TargetApi(Build.VERSION_CODES.M)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_record, container, false
        )
        root = context!!.getExternalFilesDir(null)!!
        val connectedRef = Firebase.database.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                connected = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: DatabaseError) {
                //something
            }
        })
        setHasOptionsMenu(true)
        mediaPlayer = MediaPlayer()
        args = RecordFragmentArgs.fromBundle(arguments!!)
        getAudioData(args)
        getPermissionToRecordAudio()
        binding.chronometerTimer.base = SystemClock.elapsedRealtime()
        binding.voiceRadioGroup.setOnCheckedChangeListener { _, voiceId ->
            //Toast.makeText(context , getSelectedHymnal(i), Toast.LENGTH_SHORT).show()
            if (voiceId != -1){
                binding.deleteAudioBtn.visibility = View.VISIBLE
                val voice = getSelectedVoice(voiceId)
                binding.deleteAudioBtn.isEnabled = audioData[voice]!!
            }
            else{
                binding.deleteAudioBtn.visibility = View.INVISIBLE
            }

        }
        binding.recordOrPause.setOnClickListener {
            if (isRecording) {
                binding.recordOrPause.setImageResource(R.drawable.ic_mic_black_24dp)

                pauseRecording()

            } else {
                binding.recordOrPause.setImageResource(R.drawable.ic_pause_black_24dp)
                binding.playStop.visibility = View.GONE
                binding.seekBarInRecord.visibility = View.GONE
                binding.maxDuration.visibility = View.GONE
                binding.currentDuration.visibility = View.GONE
                binding.uploadBtn.visibility = View.GONE
                binding.stopRecord.visibility = View.VISIBLE
                binding.uploadImg.setImageResource(R.drawable.ic_file_upload_black_24dp)
                startRecording()

            }
        }

        binding.stopRecord.setOnClickListener{
            stopRecording(args)
            binding.stopRecord.visibility = View.GONE

        }
        binding.playStop.setOnClickListener {
            if (isComplete) {
                initPlayer()
            } else {
                play()
            }
        }

        binding.uploadImg.setOnClickListener {
            upload(args, Uri.fromFile(File(finalFileName)))
        }

        binding.deleteAudioBtn.setOnClickListener{
            deleteAudio(args)
        }
        return binding.root
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun getPermissionToRecordAudio() {

        if (ContextCompat.checkSelfPermission(
                context!!,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                context!!,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                context!!,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            val permissionArray = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            requestPermissions(permissionArray, RECORD_AUDIO_REQUEST_CODE)

        }
    }

    // Callback with the request from calling requestPermissions(...)
    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        // Make sure it's our original READ_CONTACTS request
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.size == 3 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
                && grantResults[2] == PackageManager.PERMISSION_GRANTED
            ) {

                //Toast.makeText(this, "Record Audio permission granted", Toast.LENGTH_SHORT).show();

            } else {
                Toast.makeText(
                    context,
                    "You must give permissions to use this app. App is exiting.",
                    Toast.LENGTH_SHORT
                ).show()
                finishAffinity(requireActivity())
            }
        }

    }

    private fun startRecording() {
        isRecording = true
        isComplete = true
        pause()
        binding.uploadProgress.visibility = View.GONE
        mediaRecorder = MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(16 * 44100)
        mediaRecorder.setAudioSamplingRate(44100)
        val file =
            File(root.absolutePath + "/Audios")
        if (!file.exists()) {
            file.mkdirs()
        }
        fileName = root.absolutePath + "/Audios/" + System.currentTimeMillis() + ".mp3"
        mediaRecorder.setOutputFile(fileName)
        try {
            mediaRecorder.prepare()
            mediaRecorder.start()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        if(!isPaused){
            binding.chronometerTimer.base = SystemClock.elapsedRealtime()
            timeWhenStopped = 0
        }
        else{
            binding.chronometerTimer.base = SystemClock.elapsedRealtime() + timeWhenStopped
        }
        binding.chronometerTimer.start()
    }

    private fun stopRecording(args: RecordFragmentArgs) {

            if(isRecording){
                audioList.add(fileName)
                mediaRecorder.stop()
                mediaRecorder.release()
            }
            binding.chronometerTimer.stop()
            durationMin = binding.chronometerTimer.text.toString()
            binding.chronometerTimer.base = SystemClock.elapsedRealtime()
            timeWhenStopped = 0
            val file =
                File(root.absolutePath + "/Audios")
            if (!file.exists()) {
                file.mkdirs()
            }
            finalFileName = root.absolutePath + "/Audios/" + args.hymnId + ".mp3"
            if (audioList.size > 1) {
                binding.progressBar.visibility = View.VISIBLE
                Log.e("tested", audioList.size.toString())
                val audioParts = audioList.toTypedArray()
                uiScope.launch {
                    Log.e("tested", audioParts.size.toString())
                    mergeMediaFilesAsync(true, audioParts, finalFileName).await()
                    deleteIntermediateAudios(audioParts)
                }
            }
            else{
                val audio = File(fileName)
                audio.renameTo(File(finalFileName))
                binding.progressBar.visibility = View.GONE
                binding.playStop.visibility = View.VISIBLE
                binding.seekBarInRecord.visibility = View.VISIBLE
                binding.currentDuration.visibility = View.VISIBLE
                binding.maxDuration.visibility = View.VISIBLE
                binding.uploadBtn.visibility = View.VISIBLE
                binding.currentDuration.text = getString(R.string.initDuration)
                binding.recordOrPause.setImageResource(R.drawable.ic_mic_black_24dp)
            }
            isRecording = false
            isPaused = false
            audioList.clear()


    }

    private fun createTimeLabel(duration: Int): String {
        var timeLabel = ""
        val min = duration / 1000 / 60
        val sec = duration / 1000 % 60
        timeLabel += "$min:"
        if (sec < 10) timeLabel += "0"
        timeLabel += sec
        return timeLabel
    }

    private val handler: Handler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
//            Log.i("handler ", "handler called");
            val currentPosition: Int = msg.what
            binding.seekBarInRecord.progress = currentPosition
            val cTime = createTimeLabel(currentPosition)
            binding.currentDuration.text = cTime
        }
    }


    private fun play() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            binding.playStop.setImageResource(R.drawable.ic_pause_circle_outline_black_24dp)
        } else {
            pause()
        }
    }

    private fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            binding.playStop.setImageResource(R.drawable.ic_play_circle_outline_black_24dp)
        }
    }

    private fun initPlayer() {
        mediaPlayer = MediaPlayer()
        isComplete = false
        mediaPlayer.setDataSource(finalFileName)
        mediaPlayer = MediaPlayer.create(context, Uri.fromFile(File(finalFileName)))
        mediaPlayer.setOnPreparedListener {
            binding.maxDuration.text = createTimeLabel(mediaPlayer.duration)
            binding.seekBarInRecord.max = mediaPlayer.duration
            mediaPlayer.start()
            binding.playStop.setImageResource(R.drawable.ic_pause_circle_outline_black_24dp)
        }

        Thread(Runnable {
            while (true) {
                try {
                    val msg = Message()
                    msg.what = mediaPlayer.currentPosition
                    handler.sendMessage(msg)
                    //Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }).start()

        mediaPlayer.setOnCompletionListener {
            binding.playStop.setImageResource(R.drawable.ic_play_circle_outline_black_24dp)
            isComplete = true
        }

        binding.seekBarInRecord.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    isComplete = false
                    mediaPlayer.seekTo(progress)
                    binding.seekBarInRecord.progress = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun upload(args: RecordFragmentArgs, fileUri: Uri) {
        if(connected){
            val radioBtnId = binding.voiceRadioGroup.checkedRadioButtonId
            if (radioBtnId == -1) {
                Snackbar.make(
                        binding.root,
                        context!!.getString(R.string.select_voice_err),
                        Snackbar.LENGTH_SHORT
                    )
                    .setAction("Action", null).show()
            }
            else {
                if(audioData[getSelectedVoice(radioBtnId)]!!){
                    AlertDialog.Builder(context!!)
                        .setTitle("Replace Audio?")
                        .setMessage("You are attempting to replace an audio file, continue?")
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            binding.recordOrPause.visibility = View.INVISIBLE

                            val thisAudioRef = storageRef.child(args.hymnId + getSelectedVoice(radioBtnId))
                            val uploadTask = thisAudioRef.putFile(fileUri, metadata)
                            Snackbar.make(
                                    binding.root,
                                    "Starting upload...",
                                    Snackbar.LENGTH_LONG
                                )
                                .setAction("Action", null).show()
                            uploadTask.addOnFailureListener {
                                // Handle unsuccessful uploads
                            }.addOnSuccessListener {
                                // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
                                // ...
                                // binding.uploadProgress.visibility = View.VISIBLE
                                audioRecords.child(args.hymnId).child(getSelectedVoice(radioBtnId).toLowerCase(Locale.ENGLISH)).setValue(true)
                                binding.uploadImg.setImageResource(R.drawable.ic_check_black_24dp)
                                Snackbar.make(
                                        binding.root,
                                        "Done uploading",
                                        Snackbar.LENGTH_SHORT
                                    )
                                    .setAction("Action", null).show()
                                binding.recordOrPause.visibility = View.VISIBLE
                                binding.voiceRadioGroup.clearCheck()

                            }
                            binding.uploadProgress.visibility = View.VISIBLE
                            uploadTask.addOnProgressListener { taskSnapshot ->
                                val progress = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount
                                binding.uploadProgress.progress = progress.toInt()
                            }.addOnPausedListener {

                            }
                        }
                        .setNegativeButton(android.R.string.no, null)
                        .setIcon(R.drawable.ic_delete_forever_black_24dp)
                        .show()
                }
                else{
                    binding.recordOrPause.visibility = View.INVISIBLE
                    val thisAudioRef = storageRef.child(args.hymnId + getSelectedVoice(radioBtnId))
                    val uploadTask = thisAudioRef.putFile(fileUri, metadata)
                    Snackbar.make(
                            binding.root,
                            "Starting upload...",
                            Snackbar.LENGTH_LONG
                        )
                        .setAction("Action", null).show()
                    uploadTask.addOnFailureListener {
                        // Handle unsuccessful uploads
                    }.addOnSuccessListener {
                        // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
                        // ...
                        binding.progressBar.visibility = View.GONE
                        audioRecords.child(args.hymnId).child(getSelectedVoice(radioBtnId).toLowerCase(Locale.ENGLISH)).setValue(true)
                        binding.uploadImg.setImageResource(R.drawable.ic_check_black_24dp)
                        Snackbar.make(
                                binding.root,
                                "Done uploading",
                                Snackbar.LENGTH_SHORT
                            )
                            .setAction("Action", null).show()
                        binding.recordOrPause.visibility = View.VISIBLE
                        binding.voiceRadioGroup.clearCheck()

                    }
                    binding.uploadProgress.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.VISIBLE
                    uploadTask.addOnProgressListener { taskSnapshot ->
                        val progress = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount
                        binding.uploadProgress.progress = progress.toInt()
                        if(binding.uploadProgress.progress > 0){
                            binding.progressBar.visibility = View.GONE
                        }
                    }.addOnPausedListener {

                    }
                }

            }
        }
        else{
            Snackbar.make(
                    binding.root,
                    "Please turn on internet connection",
                    Snackbar.LENGTH_LONG
                )
                .setAction("Action", null).show()
        }

    }

    private fun mergeMediaFilesAsync(
        isAudio: Boolean,
        sourceFiles: Array<String>,
        targetFile: String
    ): Deferred<Boolean> = uiScope.async(Dispatchers.Main) {
        try {
            val mediaKey = if (isAudio) "soun" else "vide"
            val listMovies = ArrayList<Movie>()
            for (filename in sourceFiles) {
                listMovies.add(MovieCreator.build(filename))
            }
            val listTracks = LinkedList<Track>()
            for (movie in listMovies) {
                for (track in movie.tracks) {
                    if (track.handler == mediaKey) {
                        listTracks.add(track)
                    }
                }
            }
            val outputMovie = Movie()
            if (!listTracks.isEmpty()) {
                outputMovie.addTrack(
                    AppendTrack(
                        *listTracks.toArray(
                            arrayOfNulls<Track>(
                                listTracks.size
                            )
                        )
                    )
                )
            }
            val container: Container = DefaultMp4Builder().build(outputMovie)
            val fileChannel: FileChannel =
                RandomAccessFile(String.format(targetFile), "rw").channel
            container.writeContainer(fileChannel)
            fileChannel.close()
            binding.progressBar.visibility = View.GONE
            binding.playStop.visibility = View.VISIBLE
            binding.seekBarInRecord.visibility = View.VISIBLE
            binding.currentDuration.visibility = View.VISIBLE
            binding.maxDuration.visibility = View.VISIBLE
            binding.uploadBtn.visibility = View.VISIBLE
            binding.currentDuration.text = getString(R.string.initDuration)
            binding.recordOrPause.setImageResource(R.drawable.ic_mic_black_24dp)
            true
        } catch (e: IOException) {
            Log.e("Record Fragment", "Error merging media files. exception: " + e.message)
            false
        }
    }

    private fun pauseRecording(){
        audioList.add(fileName)
        isRecording = false
        isPaused = true
        mediaRecorder.stop()
        mediaRecorder.release()
        timeWhenStopped = binding.chronometerTimer.base - SystemClock.elapsedRealtime()
        binding.chronometerTimer.stop()
        Snackbar.make(
                binding.root,
                context!!.getString(R.string.rec_paused),
                Snackbar.LENGTH_SHORT
            )
            .setAction("Action", null).show()
    }

    private fun getSelectedVoice(radioBtnId: Int): String {

        val radioBtn = binding.voiceRadioGroup.findViewById<RadioButton>(radioBtnId)
        return when(radioBtn.text.toString()){
            "S" -> "soprano"
            "A" -> "alto"
            "T" -> "tenor"
            "B" -> "bass"
            else -> "choir"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mergeJob.cancel()
    }

    private fun getAudioData(args: RecordFragmentArgs){

        val audioDataListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for(voiceSnapshot in dataSnapshot.children){
                    audioData[voiceSnapshot.key as String] = voiceSnapshot.value as Boolean
                }

                binding.voiceRadioGroup.visibility = View.VISIBLE
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        audioRecords.child(args.hymnId).addValueEventListener(audioDataListener)
    }

    private fun deleteAudio(args: RecordFragmentArgs){
        binding.progressBar.visibility = View.VISIBLE
        val radioBtnId = binding.voiceRadioGroup.checkedRadioButtonId
        val audioRef = storageRef.child(args.hymnId + getSelectedVoice(radioBtnId))
// Delete the file
        audioRef.delete().addOnSuccessListener {
            binding.progressBar.visibility = View.GONE
            audioRecords.child(args.hymnId).child(getSelectedVoice(radioBtnId).toLowerCase(Locale.ENGLISH)).setValue(false)
            Snackbar.make(
                    binding.root,
                    "Audio deleted",
                    Snackbar.LENGTH_SHORT
                )
                .setAction("Action", null).show()
            binding.voiceRadioGroup.clearCheck()
        }.addOnFailureListener {
            // Uh-oh, an error occurred!
            Snackbar.make(
                    binding.root,
                    "Error occurred while deleting!",
                    Snackbar.LENGTH_SHORT
                )
                .setAction("Action", null).show()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.record_option_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.chooseFile -> {
                binding.uploadImg.setImageResource(R.drawable.ic_file_upload_black_24dp)
                var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
                chooseFile.type = "audio/*"
                chooseFile = Intent.createChooser(chooseFile, "Choose a file")
                startActivityForResult(chooseFile, PICKFILE_RESULT_CODE)
                true
            }
            else -> false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PICKFILE_RESULT_CODE -> if (resultCode == -1) {
                binding.uploadBtn.visibility = View.VISIBLE
                binding.uploadImg.isEnabled = false
                binding.uploadProgress.visibility = View.VISIBLE
                binding.uploadProgress.progress = 0
                if (data != null && data.data != null) {
                    val fileUri = data.data!!
                    upload(args, fileUri)
                }
            }
        }
    }

    private fun deleteIntermediateAudios(audioList: Array<String>){
        for (fileName in audioList){
            val file = File(fileName)
            file.delete()
        }
    }

    companion object {
        const val PICKFILE_RESULT_CODE = 1
        private const val RECORD_AUDIO_REQUEST_CODE = 123
    }
}
