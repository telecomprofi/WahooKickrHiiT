package com.telecomprofi.kickrhiit.ui.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telecomprofi.kickrhiit.Graph
import com.telecomprofi.kickrhiit.core.RiderProfile
import com.telecomprofi.kickrhiit.core.Workout
import com.telecomprofi.kickrhiit.core.Workouts
import com.telecomprofi.kickrhiit.data.db.CatalogueEntity
import com.telecomprofi.kickrhiit.data.db.RideEntity
import com.telecomprofi.kickrhiit.data.db.TrackEntity
import com.telecomprofi.kickrhiit.data.prefs.AppSettings
import com.telecomprofi.kickrhiit.data.prefs.CoachSettings
import com.telecomprofi.kickrhiit.data.prefs.DeviceSettings
import com.telecomprofi.kickrhiit.data.prefs.MusicSettings
import com.telecomprofi.kickrhiit.data.prefs.RideSettings
import com.telecomprofi.kickrhiit.hrm.BleHeartRateMonitor
import com.telecomprofi.kickrhiit.hrm.HrmState
import com.telecomprofi.kickrhiit.hrm.ScannedDevice
import com.telecomprofi.kickrhiit.music.AnalysisProgress
import com.telecomprofi.kickrhiit.music.CatalogueReport
import com.telecomprofi.kickrhiit.ride.RideHub
import com.telecomprofi.kickrhiit.trainer.TrainerControl
import com.telecomprofi.kickrhiit.trainer.TrainerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared by every screen: the settings, and whatever the ride is doing. */
abstract class BaseViewModel : ViewModel() {
    protected val container = Graph.container

    val settings: StateFlow<AppSettings> = container.prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val ride = RideHub.state
}

class HomeViewModel : BaseViewModel() {

    val workouts: StateFlow<List<Workout>> = container.prefs.settings
        .map { Workouts.all(it.ride.sprintResistance) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Workouts.all())

    val selected: StateFlow<Workout> = container.prefs.settings
        .map { Workouts.byId(it.ride.selectedWorkoutId, it.ride.sprintResistance) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Workouts.all().first())

    /** Everything ridden in the last seven days, oldest first. */
    val week: StateFlow<List<RideEntity>> =
        container.rideDao.since(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<RideEntity>> = container.rideDao.recent(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastRide = RideHub.lastRide
    val note = RideHub.note

    fun select(workout: Workout) = viewModelScope.launch {
        val current = container.prefs.settings.first().ride
        container.prefs.updateRide(current.copy(selectedWorkoutId = workout.id))
    }

    fun dismissRide() = RideHub.consumeRide()
}

class RideViewModel : BaseViewModel()

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = Graph.container
    private val hrm = BleHeartRateMonitor(app)
    private val trainer = TrainerControl(app)

    val settings: StateFlow<AppSettings> = container.prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _hrmDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val hrmDevices: StateFlow<List<ScannedDevice>> = _hrmDevices.asStateFlow()

    private val _trainerDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val trainerDevices: StateFlow<List<ScannedDevice>> = _trainerDevices.asStateFlow()

    val hrmState: StateFlow<HrmState> = hrm.state
    val trainerState: StateFlow<TrainerState> = trainer.state
    val trainerResponse: StateFlow<String?> = trainer.lastResponse

    private var hrmScan: Job? = null
    private var trainerScan: Job? = null

    fun updateRider(transform: (RiderProfile) -> RiderProfile) = viewModelScope.launch {
        container.prefs.updateRider(transform(container.prefs.settings.first().rider))
    }

    fun updateDevices(transform: (DeviceSettings) -> DeviceSettings) = viewModelScope.launch {
        container.prefs.updateDevices(transform(container.prefs.settings.first().devices))
    }

    fun updateCoach(transform: (CoachSettings) -> CoachSettings) = viewModelScope.launch {
        container.prefs.updateCoach(transform(container.prefs.settings.first().coach))
    }

    fun updateRide(transform: (RideSettings) -> RideSettings) = viewModelScope.launch {
        container.prefs.updateRide(transform(container.prefs.settings.first().ride))
    }

    fun scanTrainer(filter: Boolean = true) {
        trainerScan?.cancel()
        _trainerDevices.value = emptyList()
        trainerScan = viewModelScope.launch {
            trainer.scan(filter).collect { _trainerDevices.value = it }
        }
    }

    fun scanHrm(filter: Boolean = true) {
        hrmScan?.cancel()
        _hrmDevices.value = emptyList()
        hrmScan = viewModelScope.launch { hrm.scan(filter).collect { _hrmDevices.value = it } }
    }

    fun stopScans() {
        trainerScan?.cancel(); trainerScan = null
        hrmScan?.cancel(); hrmScan = null
    }

    fun pairTrainer(device: ScannedDevice) {
        updateDevices { it.copy(trainerAddress = device.address, trainerName = device.name) }
        stopScans()
    }

    fun pairHrm(device: ScannedDevice) {
        updateDevices { it.copy(hrmAddress = device.address, hrmName = device.name, simulate = false) }
        stopScans()
    }

    fun forgetTrainer() = updateDevices { it.copy(trainerAddress = null, trainerName = null) }
    fun forgetHrm() = updateDevices { it.copy(hrmAddress = null, hrmName = null) }

    override fun onCleared() {
        stopScans()
        hrm.stop()
        trainer.stop()
        super.onCleared()
    }
}

class MusicViewModel : BaseViewModel() {

    private val library = container.musicLibrary
    private val catalogueDao = container.db.catalogueDao()

    val music: StateFlow<MusicSettings> = container.prefs.settings
        .map { it.music }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MusicSettings())

    val tracks: StateFlow<List<TrackEntity>> = container.db.trackDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val catalogueCount: StateFlow<Int> = catalogueDao.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<CatalogueEntity>>(emptyList())
    val results: StateFlow<List<CatalogueEntity>> = _results.asStateFlow()

    private val _report = MutableStateFlow<CatalogueReport?>(null)
    val report: StateFlow<CatalogueReport?> = _report.asStateFlow()

    private val _progress = MutableStateFlow<AnalysisProgress?>(null)
    val progress: StateFlow<AnalysisProgress?> = _progress.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private var job: Job? = null

    fun setQuery(value: String) {
        _query.value = value
        viewModelScope.launch { _results.value = catalogueDao.search(value.trim()) }
    }

    fun update(transform: (MusicSettings) -> MusicSettings) = viewModelScope.launch {
        container.prefs.updateMusic(transform(container.prefs.settings.first().music))
    }

    fun setFolder(uri: String) = viewModelScope.launch {
        container.prefs.updateMusic(container.prefs.settings.first().music.copy(musicTreeUri = uri))
        scanFolder()
    }

    fun scanFolder() {
        job?.cancel()
        job = viewModelScope.launch {
            _busy.value = true
            try {
                val settings = container.prefs.settings.first().music
                val report = library.scanFolder(settings.musicTreeUri) { _status.value = it }
                _report.value = report
                _status.value = null
                setQuery(_query.value)
            } finally {
                _busy.value = false
            }
        }
    }

    fun addFromCatalogue(uri: String) = viewModelScope.launch {
        library.addToWorkout(listOf(uri))
        analyse()
    }

    fun addAllResults() = viewModelScope.launch {
        library.addToWorkout(_results.value.map { it.uri })
        analyse()
    }

    fun addFiles(uris: List<Uri>) = viewModelScope.launch {
        library.addFilesToWorkout(uris)
        analyse()
    }

    /** A tempo typed in by hand; it outranks measurement and survives a re-measure. */
    fun setBpm(track: TrackEntity, bpm: Double) = viewModelScope.launch {
        container.db.trackDao().setManualBpm(track.uri, bpm)
    }

    /** Hand the track back to the measurer. */
    fun clearBpm(track: TrackEntity) = viewModelScope.launch {
        container.db.trackDao().setAnalysis(
            uri = track.uri,
            bpm = 0.0,
            conf = 0.0,
            lufs = track.loudnessLufs,
            at = 0L,
        )
    }

    fun remove(track: TrackEntity) = viewModelScope.launch { library.removeFromWorkout(track.uri) }

    fun clear() = viewModelScope.launch { library.clearWorkout() }

    fun analyse() {
        job?.cancel()
        job = viewModelScope.launch { runAnalysis() }
    }

    /** Take tempi from the files' own tags, which needs no decoding. */
    fun readTags() {
        job?.cancel()
        job = viewModelScope.launch {
            _busy.value = true
            try {
                val dropped = library.dropUnplayable()
                val upgraded = library.adoptTags { _progress.value = it }
                _status.value = buildString {
                    append(
                        if (upgraded > 0) {
                            "$upgraded tracks took their tempo from the file's own tag"
                        } else {
                            "No usable BPM tags found in the queued files"
                        }
                    )
                    if (dropped > 0) {
                        append(" · $dropped in formats this phone cannot play were removed")
                    }
                }
            } finally {
                _busy.value = false
                _progress.value = null
            }
        }
    }

    fun remeasure() {
        job?.cancel()
        job = viewModelScope.launch {
            _busy.value = true
            try {
                library.reanalyseAll { _progress.value = it }
            } finally {
                _busy.value = false
                _progress.value = null
            }
        }
    }

    private suspend fun runAnalysis() {
        _busy.value = true
        try {
            library.analysePending { _progress.value = it }
        } finally {
            _busy.value = false
            _progress.value = null
        }
    }

    fun cancel() {
        job?.cancel()
        _busy.value = false
        _progress.value = null
        _status.value = null
    }

    fun dismissReport() { _report.value = null }
}

/** Just the two things the shell needs: the settings, and whether a ride is on. */
class RootViewModel : BaseViewModel()
