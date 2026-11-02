package dev.dadyarri.medtracker

import android.app.Application
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.*
import dev.dadyarri.medtracker.ui.AlarmViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class MedTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MedTrackerApplication)
            modules(module {
                single { Room.databaseBuilder(get(), AlarmDatabase::class.java, "alarm-spike.db").build() }
                single { AlarmController(get(), get()) }
                viewModel { AlarmViewModel(get(), get()) }
                single { get<AlarmDatabase>().pressure() }
                viewModel { dev.dadyarri.medtracker.pressure.PressureViewModel(get()) }
                single { get<AlarmDatabase>().measurements() }
                viewModel { dev.dadyarri.medtracker.meals.MealViewModel(get()) }
                single { dev.dadyarri.medtracker.measurements.MeasurementSettings(get()) }
                viewModel { dev.dadyarri.medtracker.measurements.MeasurementViewModel(get(), get()) }
            })
        }
    }
}
