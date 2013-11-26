package nl.haas_en_berg.rijksdriehoekscoordinaten;

// FIXME: remove when dropping Gingerbread support sometime

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class RDPreferenceActivity extends PreferenceActivity
{
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		// There's no getActionBar on 2.3, *AND* there's no PreferenceActivity
		// in the support library, so there's also no way to have
		// getSupportActionBar here.
		
		// we are not going to space today
		// getActionBar().setDisplayHomeAsUpEnabled(true);
	}
}
