package io.github.yue56189.caloriediary

import android.app.Application
import io.github.yue56189.caloriediary.data.AppDatabase
import io.github.yue56189.caloriediary.data.Prefs
import io.github.yue56189.caloriediary.data.Repository

class CalorieDiaryApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: Repository
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.build(this)
        repository = Repository(database, this)
        prefs = Prefs(this)
    }
}
