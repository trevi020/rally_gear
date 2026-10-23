package io.bimmergestalt.idriveconnectaddons.rallygear

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainModel : ViewModel() {

    private val _gearLiveData = MutableLiveData<String>("N")
    val gearLiveData: LiveData<String> = _gearLiveData

    private val _rpmLiveData = MutableLiveData<Int>(0)
    val rpmLiveData: LiveData<Int> = _rpmLiveData

    // Aggiornamento dati dal Broadcast/Service di AAIdrive
    fun updateCarData(rawGear: Int?, rpm: Int?) {
        val displayGear = when (rawGear) {
            0 -> "N"
            -1, 15 -> "R"
            1 -> "1"
            2 -> "2"
            3 -> "3"
            4 -> "4"
            5 -> "5"
            6 -> "6"
            7 -> "7"
            8 -> "8"
            else -> rawGear?.toString() ?: "N"
        }
        _gearLiveData.postValue(displayGear)
        _rpmLiveData.postValue(rpm ?: 0)
    }

    companion object {
        val instance = MainModel()
    }
}