package com.vitalware.succ


import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.applySkeleton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.firebase.storage.ktx.storageMetadata
import com.varunest.sparkbutton.SparkButton
import com.varunest.sparkbutton.SparkEventListener
import com.vitalware.succ.databinding.FragmentSingingBinding
import java.io.File
import java.text.Normalizer
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class SingingFragment : Fragment() {
    private lateinit var binding: FragmentSingingBinding
    private val regex = Regex("[^A-Za-z0-9 ]")
    private var mediaPlayer = MediaPlayer()
    private lateinit var skeleton: Skeleton
    private var audioRecords: DatabaseReference = Firebase.database.getReference("audios")
    private var audioDataGotten = false
    private val storageRef = Firebase.storage.getReference("audios")
    private val storageScores = Firebase.storage.getReference("musicScores")
    private var database: DatabaseReference = Firebase.database.getReference("titlesAndVerses")
    private var hymnListDatabase: DatabaseReference =
        Firebase.database.getReference("searchRedundancy")
    private var currentTrack = "None"
    private var isComplete = false
    private var connected = false
    private lateinit var args: SingingFragmentArgs
    private lateinit var adapter: HymnAdapter
    private val metadata = storageMetadata {
        contentType = "doc/pdf"
    }
    private lateinit var root: File
    private var musicScores: DatabaseReference = Firebase.database.getReference("musicScores")
    private lateinit var connectedListener: ValueEventListener
    private val connectedRef = Firebase.database.getReference(".info/connected")
    private lateinit var verseListener: ValueEventListener
    @TargetApi(Build.VERSION_CODES.M)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_singing, container, false
        )
        root = context!!.getExternalFilesDir(null)!!
        database.keepSynced(true)
        skeleton = binding.songVerses.applySkeleton(R.layout.verse_view, 7)
        skeleton.showSkeleton()
        binding.songVerses.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                connected = snapshot.getValue(Boolean::class.java) ?: false
                notifyNoInternet(connected)
            }

            override fun onCancelled(error: DatabaseError) {
                //something
            }
        }

        connectedRef.addValueEventListener(connectedListener)
        args = SingingFragmentArgs.fromBundle(arguments!!)
        getPermissionToRecordAudio()
        getAudioData(args)
        getMusicScoreData()

        binding.musicScoreBtn.setOnClickListener{
            openMusicScore()
        }
        binding.musicScoreBtn.setOnLongClickListener{
            deleteScore()
            true
        }

        PreferenceManager.getDefaultSharedPreferences(context).apply {
            when (getInt(AuthCodeFragment.USER_ACCESS_LEVEL, 1)) {
                1 -> {
                    setHasOptionsMenu(false)
                }
                else -> {
                    setHasOptionsMenu(true)
                }
            }
        }
        if (activity is AppCompatActivity) {
            (activity as AppCompatActivity).supportActionBar!!.title = args.hymnTitle
        }
        binding.textViewSoprano.setOnLongClickListener {
            deleteAudioLocal(it.id)
            true
        }
        binding.textViewAlto.setOnLongClickListener {
            deleteAudioLocal(it.id)
            true
        }
        binding.textViewTenor.setOnLongClickListener {
            deleteAudioLocal(it.id)
            true
        }
        binding.textViewBass.setOnLongClickListener {
            deleteAudioLocal(it.id)
            true
        }
        binding.textViewChoir.setOnLongClickListener {
            deleteAudioLocal(it.id)
            true
        }

        val sparkListenerSop = object : SparkEventListener {
            override fun onEventAnimationEnd(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation ended")
            }

            override fun onEvent(button: ImageView?, buttonState: Boolean) {
                playLogic(binding.buttonSoprano)
            }

            override fun onEventAnimationStart(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation Started")
            }
        }

        val sparkListenerAlto = object : SparkEventListener {
            override fun onEventAnimationEnd(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation ended")
            }

            override fun onEvent(button: ImageView?, buttonState: Boolean) {
                playLogic(binding.buttonAlto)
            }

            override fun onEventAnimationStart(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation Started")
            }
        }

        val sparkListenerTenor = object : SparkEventListener {
            override fun onEventAnimationEnd(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation ended")
            }

            override fun onEvent(button: ImageView?, buttonState: Boolean) {
                playLogic(binding.buttonTenor)
            }

            override fun onEventAnimationStart(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation Started")
            }
        }

        val sparkListenerBass = object : SparkEventListener {
            override fun onEventAnimationEnd(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation ended")
            }

            override fun onEvent(button: ImageView?, buttonState: Boolean) {
                playLogic(binding.buttonBass)
            }

            override fun onEventAnimationStart(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation Started")
            }
        }

        val sparkListenerChoir = object : SparkEventListener {
            override fun onEventAnimationEnd(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation ended")
            }

            override fun onEvent(button: ImageView?, buttonState: Boolean) {
                playLogic(binding.buttonChoir)
            }

            override fun onEventAnimationStart(button: ImageView?, buttonState: Boolean) {
                Log.i("SingingFragment", "Animation Started")
            }
        }

        binding.buttonSoprano.setEventListener(sparkListenerSop)
        binding.buttonAlto.setEventListener(sparkListenerAlto)
        binding.buttonTenor.setEventListener(sparkListenerTenor)
        binding.buttonBass.setEventListener(sparkListenerBass)
        binding.buttonChoir.setEventListener(sparkListenerChoir)

        adapter = HymnAdapter(VerseListener { verse, view ->
            //edit or delete verse menu
            //dialog fragment
            val popup = PopupMenu(context, view)
            popup.menuInflater.inflate(R.menu.options_menu_verse, popup.menu)
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.edit_verse -> {
                        NavHostFragment.findNavController(this@SingingFragment)
                            .navigate(
                                SingingFragmentDirections.actionSingingFragmentToEditVerseFragment(
                                    verse.verseText,
                                    verse.verseId,
                                    verse.isChorus, args.hymnId,
                                    args.hymnTitle
                                )
                            )
                    }

                    R.id.delete_verse -> {
                        deleteVerse(
                            verse.verseId,
                            args.hymnId,
                            verse.verseText,
                            args.hymnTitle
                        )
                    }
                }
                true

            }

            popup.show()

        })
        if (binding.songVerses.itemDecorationCount == 0) {
            val divider = DividerItemDecoration(
                binding.songVerses.context,
                LinearLayoutManager.VERTICAL
            )
            binding.songVerses.addItemDecoration(divider)
        }
        verseListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                makeNotificationInvisible()
                val data = mutableListOf<Verse>()
                for (packSnapshot in dataSnapshot.children) {
                    val verse = Verse(
                        packSnapshot.child("verseText").value as String,
                        packSnapshot.child("chorus").value as Boolean,
                        packSnapshot.key!!
                    )
                    data.add(verse)
                }

                skeleton.showOriginal()
                adapter.submitList(data)

                binding.songVerses.adapter = adapter
                binding.songVerses.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        database.child(args.hymnId).addValueEventListener(verseListener)

        return binding.root
    }

    private fun deleteVerse(verseId: String, hymnId: String, verseText: String, hymnTitle: String) {
        database.child(hymnId).child(verseId).removeValue()
        val verseSplitted = verseText.split(" ")
        val titleSplit = hymnTitle.split(" ")
        for (verseLing in verseSplitted) {
            //hymnListDatabase.child(args.hymnId).child("title").child(verseLing).setValue(true)
            if (!titleSplit.contains(verseLing)) {
                var semiPurgedVerseLing = Normalizer.normalize(verseLing, Normalizer.Form.NFD)
                semiPurgedVerseLing = semiPurgedVerseLing.replace("[^\\p{ASCII}]", "")
                val purgedVerseLing = regex.replace(semiPurgedVerseLing, "")
                hymnListDatabase.child(hymnId).child(purgedVerseLing.toLowerCase(Locale.ENGLISH))
                    .removeValue()
            }
        }
        Snackbar.make(
                binding.root,
                this@SingingFragment.resources.getString(R.string.verse_del),
                Snackbar.LENGTH_LONG
            )
            .setAction("Action", null).show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.singing_fragment_menu, menu)
        PreferenceManager.getDefaultSharedPreferences(context).apply {
            when (getInt(AuthCodeFragment.USER_ACCESS_LEVEL, 1)) {
                2 -> {
                    menu.removeItem(R.id.recordFragment)
                    menu.removeItem(R.id.add_music_score)
                }
                3 -> {
                    val scoreListener = object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if(isAdded){
                                menu[4].isVisible = dataSnapshot.child("hasMusicScore").value as Boolean
                            }
                        }

                        override fun onCancelled(databaseError: DatabaseError) {
                            // Getting Post failed, log a message
                            // ...
                        }
                    }
                    musicScores.child(args.hymnId).addValueEventListener(scoreListener)
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.listen_to_audio_item -> {
                //setUpAudioCard()
                if(audioDataGotten){
                    binding.cardView.visibility = View.VISIBLE
                }
                return true
            }
            R.id.recordFragment -> {
                NavHostFragment.findNavController(this@SingingFragment)
                    .navigate(

                        SingingFragmentDirections.actionSingingFragmentToRecordFragment(
                            args.hymnId
                        )
                    )
                return true
            }
            R.id.delAudio -> {
                Toast.makeText(
                    context,
                    "To delete an audio hold its voice label.",
                    Toast.LENGTH_LONG
                ).show()
                return true
            }
            R.id.add_music_score -> {
                var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
                chooseFile.type = "application/pdf"
                chooseFile = Intent.createChooser(chooseFile, "Choose music score file")
                startActivityForResult(chooseFile, RecordFragment.PICKFILE_RESULT_CODE)
                return true
            }
            R.id.del_score -> {
                deleteMusicScoreFromCloud()
                return true
            }
            else -> return false
        }
    }

    private fun getMusicScoreData(){
        val scoreListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                binding.musicScoreBtn.isVisible = dataSnapshot.child("hasMusicScore").value as Boolean
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        musicScores.child(args.hymnId).addListenerForSingleValueEvent(scoreListener)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RecordFragment.PICKFILE_RESULT_CODE -> if (resultCode == -1) {
                binding.progressBar3.visibility = View.VISIBLE
                if (data != null && data.data != null) {
                    val fileUri = data.data!!
                    uploadMusicScore(args, fileUri)
                }
            }
        }
    }

    private fun deleteMusicScoreFromCloud(){
        storageScores.child(args.hymnId).delete().addOnSuccessListener {
            musicScores.child(args.hymnId)
                .child("hasMusicScore").setValue(false).addOnCompleteListener {
                    binding.musicScoreBtn.visibility = View.GONE
                }
        }
    }
    private fun uploadMusicScore(args: SingingFragmentArgs, fileUri: Uri) {
        if(binding.musicScoreBtn.visibility == View.VISIBLE){
            Builder(context!!)
                .setTitle("Replace music score?")
                .setMessage("Are you sure you want to replace this music score?")
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    val uploadTask = storageScores.child(args.hymnId).putFile(fileUri, metadata)
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
                        binding.progressBar3.visibility = View.GONE
                        musicScores.child(args.hymnId)
                            .child("hasMusicScore").setValue(true)
                        Snackbar.make(
                                binding.root,
                                "Done uploading",
                                Snackbar.LENGTH_SHORT
                            )
                            .setAction("Action", null).show()
                    }
                }
                .setNegativeButton(android.R.string.no, null)
                .setIcon(R.drawable.ic_delete_forever_black_24dp)
                .show()
        }
        else {
            val uploadTask = storageScores.child(args.hymnId).putFile(fileUri, metadata)
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
                binding.progressBar3.visibility = View.GONE
                musicScores.child(args.hymnId)
                    .child("hasMusicScore").setValue(true)
                Snackbar.make(
                        binding.root,
                        "Done uploading",
                        Snackbar.LENGTH_SHORT
                    )
                    .setAction("Action", null).show()
            }
        }
    }

    private fun getAudioData(args: SingingFragmentArgs) {

        val audioDataListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                audioDataGotten = true
                for (voiceSnapshot in dataSnapshot.children) {
                    val voice = voiceSnapshot.value as Boolean
                    when (voiceSnapshot.key) {
                        "soprano" -> {
                            binding.buttonSoprano.isEnabled = voice
                            binding.textViewSoprano.isLongClickable = voice
                            if (!voice) {
                                binding.buttonSoprano.setInactiveImage(R.drawable.ic_do_not_disturb_alt_black_24dp)
                            }
                        }
                        "tenor" -> {
                            binding.buttonTenor.isEnabled = voice
                            binding.textViewTenor.isLongClickable = voice
                            if (!voice) {
                                binding.buttonTenor.setInactiveImage(R.drawable.ic_do_not_disturb_alt_black_24dp)
                            }
                        }
                        "alto" -> {
                            binding.buttonAlto.isEnabled = voice
                            binding.textViewAlto.isLongClickable = voice
                            if (!voice) {
                                binding.buttonAlto.setInactiveImage(R.drawable.ic_do_not_disturb_alt_black_24dp)
                            }
                        }
                        "bass" -> {
                            binding.buttonBass.isEnabled = voice
                            binding.textViewBass.isLongClickable = voice
                            if (!voice) {
                                binding.buttonBass.setInactiveImage(R.drawable.ic_do_not_disturb_alt_black_24dp)
                            }
                        }
                        "choir" -> {
                            binding.buttonChoir.isEnabled = voice
                            binding.textViewChoir.isLongClickable = voice
                            if (!voice) {
                                binding.buttonChoir.setInactiveImage(R.drawable.ic_do_not_disturb_alt_black_24dp)
                            } else {
                                binding.sparkButtonChoir.visibility = View.VISIBLE
                                binding.textViewChoir.visibility = View.VISIBLE
                            }
                        }
                    }
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        audioRecords.child(args.hymnId).addValueEventListener(audioDataListener)
    }

    private fun getAudioFile(id: Int): String {
        val voiceSuffix = when (id) {
            binding.buttonAlto.id -> "alto"
            binding.buttonSoprano.id -> "soprano"
            binding.buttonTenor.id -> "tenor"
            binding.buttonBass.id -> "bass"
            else -> "choir"
        }
        val file =
            File(root.absolutePath + "/Audios")
        if (!file.exists()) {
            file.mkdirs()
        }
        return root.absolutePath + "/Audios/" + args.hymnId + voiceSuffix + ".mp3"
    }


    private fun checkIfAudioExists(id: Int): Boolean {
        val voiceSuffix = when (id) {
            binding.buttonAlto.id -> "alto"
            binding.buttonSoprano.id -> "soprano"
            binding.buttonTenor.id -> "tenor"
            binding.buttonBass.id -> "bass"
            else -> "choir"
        }
        val file =
            File(root.absolutePath + "/Audios")
        if (!file.exists()) {
            file.mkdirs()
        }
        val fileName = root.absolutePath + "/Audios/" + args.hymnId + voiceSuffix + ".mp3"
        return File(fileName).exists()
    }


    private fun downloadAudio(buttonSpark: SparkButton) {
        if (connected) {
            Toast.makeText(context, "Downloading", Toast.LENGTH_SHORT).show()
            val voiceSuffix = when (buttonSpark.id) {
                binding.buttonAlto.id -> "alto"
                binding.buttonSoprano.id -> "soprano"
                binding.buttonTenor.id -> "tenor"
                binding.buttonBass.id -> "bass"
                else -> "choir"
            }
            val progressBar = when (buttonSpark.id) {
                binding.buttonAlto.id -> binding.downloadProgressAlto
                binding.buttonSoprano.id -> binding.downloadProgressSoprano
                binding.buttonTenor.id -> binding.downloadProgressTenor
                binding.buttonBass.id -> binding.downloadProgressBass
                else -> binding.downloadProgressChoir
            }
            progressBar.visibility = View.VISIBLE
            val file =
                File(root.absolutePath + "/Audios")
            if (!file.exists()) {
                file.mkdirs()
            }
            val audioRef = storageRef.child(args.hymnId + voiceSuffix)
            val localFile =
                File(root.absolutePath + "/Audios/" + args.hymnId + voiceSuffix + ".mp3")
            val downloadTask = audioRef.getFile(localFile)
            buttonSpark.isEnabled = false
            downloadTask.addOnSuccessListener {
                // Local temp file has been created
                playLogic(buttonSpark)
                binding.seekBar.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                buttonSpark.isEnabled = true
            }.addOnFailureListener {
                // Handle any errors
            }

            downloadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount
                progressBar.progress = progress.toInt()
            }

        } else {
            Snackbar.make(
                    binding.root,
                    "Please turn on internet connection",
                    Snackbar.LENGTH_LONG
                )
                .setAction("Action", null).show()
        }

    }

    private fun getFileName(id: Int): String {
        val voiceSuffix = when (id) {
            binding.textViewAlto.id -> "alto"
            binding.textViewSoprano.id -> "soprano"
            binding.textViewTenor.id -> "tenor"
            binding.textViewBass.id -> "bass"
            else -> "choir"
        }
        val file =
            File(root.absolutePath + "/Audios")
        if (!file.exists()) {
            file.mkdirs()
        }
        return root.absolutePath + "/Audios/" + args.hymnId + voiceSuffix + ".mp3"
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun getPermissionToRecordAudio() {

        if (ContextCompat.checkSelfPermission(
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
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            requestPermissions(permissionArray, REQUEST_CODE)

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
        if (requestCode == REQUEST_CODE) {
            if (grantResults.size == 2 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i("SingingFragment", "Permissions granted")
            } else {
                Toast.makeText(
                    context,
                    "You must give permissions to use this app. App is exiting.",
                    Toast.LENGTH_SHORT
                ).show()
                ActivityCompat.finishAffinity(requireActivity())
            }
        }

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
            binding.seekBar.progress = currentPosition
            val cTime = createTimeLabel(currentPosition)
            binding.currentTime.text = cTime
        }
    }

    private fun play() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
        } else {
            pause()
        }
    }

    private fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
    }

    private fun initPlayer(finalFileName: String) {
        binding.seekBar.visibility = View.VISIBLE
        currentTrack = finalFileName
        mediaPlayer = MediaPlayer.create(context, Uri.fromFile(File(finalFileName)))
        mediaPlayer.setOnPreparedListener {
            binding.maxPlayDuration.text = createTimeLabel(mediaPlayer.duration)
            binding.seekBar.max = mediaPlayer.duration
            mediaPlayer.start()
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
            isComplete = false
        }

        binding.seekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                    binding.seekBar.progress = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun deleteAudioLocal(id: Int) {
        if (File(getFileName(id)).exists()) {
            Builder(context!!)
                .setTitle("Delete Audio?")
                .setMessage("Are you sure you want to delete this audio?")
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    if (File(getFileName(id)).delete()) {
                        binding.seekBar.visibility = View.GONE
                        binding.currentTime.text = ""
                        binding.maxPlayDuration.text = ""
                        Snackbar.make(
                                binding.root,
                                "Audio deleted",
                                Snackbar.LENGTH_SHORT
                            )
                            .setAction("Action", null).show()
                    }

                }
                .setNegativeButton(android.R.string.no, null)
                .setIcon(R.drawable.ic_delete_forever_black_24dp)
                .show()
        }
        else{
            Toast.makeText(context, "Nothing to delete", Toast.LENGTH_SHORT).show()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
    }

    private fun playLogic(button: SparkButton) {
        if (checkIfAudioExists(button.id)) {
            if (currentTrack != getAudioFile(button.id)) {
                val sparkButton = when (currentTrack) {
                    getAudioFile(binding.buttonSoprano.id) -> binding.buttonSoprano
                    getAudioFile(binding.buttonAlto.id) -> binding.buttonAlto
                    getAudioFile(binding.buttonTenor.id) -> binding.buttonTenor
                    getAudioFile(binding.buttonBass.id) -> binding.buttonBass
                    else -> binding.buttonChoir
                }
                if (currentTrack != "None") {
                    sparkButton.isChecked = false
                }
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.reset()
                    initPlayer(getAudioFile(button.id))
                } else {
                    initPlayer(getAudioFile(button.id))
                }
            } else if (!isComplete && currentTrack == getAudioFile(button.id)) {
                play()
            }
        } else {
            downloadAudio(button)
        }
    }

    private fun notifyNoInternet(connected: Boolean) {
        if (!connected && binding.songVerses.adapter != adapter) {
            val handler = Handler()
            val runnableCode = Runnable {
                if (binding.songVerses.adapter != adapter) {
                    binding.songVerses.visibility = View.GONE
                    binding.oopsImage.visibility = View.VISIBLE
                    binding.dataStateText.visibility = View.VISIBLE
                }
            }
            handler.postDelayed(runnableCode, 6000)
        }
    }

    private fun makeNotificationInvisible() {
        binding.songVerses.visibility = View.VISIBLE
        binding.oopsImage.visibility = View.GONE
        binding.dataStateText.visibility = View.GONE
    }

    companion object {
        private const val REQUEST_CODE: Int = 123
    }

    private fun deleteScore(){
        val file =
            File(root.absolutePath + "/MusicScores")
        if (!file.exists()) {
            file.mkdirs()
        }
        val fileName = root.absolutePath + "/MusicScores/" + args.hymnId + ".pdf"
        if(File(fileName).exists()){
            Builder(context!!)
                .setTitle("Delete music score?")
                .setMessage("Are you sure you want to delete this music score?")
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    if (File(fileName).delete()) {
                        Snackbar.make(
                                binding.root,
                                "Music score deleted",
                                Snackbar.LENGTH_SHORT
                            )
                            .setAction("Action", null).show()
                    }

                }
                .setNegativeButton(android.R.string.no, null)
                .setIcon(R.drawable.ic_delete_forever_black_24dp)
                .show()
        }
        else{
            Toast.makeText(context, "Nothing to delete", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMusicScore(){
        val file =
            File(root.absolutePath + "/MusicScores")
        if (!file.exists()) {
            file.mkdirs()
        }
        val fileName = root.absolutePath + "/MusicScores/" + args.hymnId + ".pdf"
        val pdfFile = File(fileName)
        if(pdfFile.exists()){
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(FileProvider.getUriForFile(context!!, BuildConfig.APPLICATION_ID + ".provider", pdfFile), "application/pdf")
            intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(intent)
        }else{
            binding.progressBar3.visibility = View.VISIBLE
            val localFile = File(root.absolutePath + "/MusicScores/" + args.hymnId + ".pdf")
            val downloadTask = storageScores.child(args.hymnId).getFile(localFile)
            downloadTask.addOnSuccessListener {
                // Local temp file has been created
                binding.progressBar3.visibility = View.GONE
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(FileProvider.getUriForFile(context!!, BuildConfig.APPLICATION_ID + ".provider", localFile), "application/pdf")
                intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                startActivity(intent)
            }.addOnFailureListener {
                // Handle any errors
                binding.progressBar3.visibility = View.GONE
                Toast.makeText(context, "Error occurred while downloading", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        connectedRef.removeEventListener(connectedListener)
        database.child(args.hymnId).removeEventListener(verseListener)
    }
}
