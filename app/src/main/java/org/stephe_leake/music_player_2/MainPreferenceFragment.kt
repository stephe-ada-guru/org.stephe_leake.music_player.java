package org.stephe_leake.music_player_2

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class MainPreferenceFragment : PreferenceFragmentCompat()
{
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
    {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
