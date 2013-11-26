package nl.haas_en_berg.rijksdriehoekscoordinaten;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class RDMainActivity extends FragmentActivity
{
	View fragment_view = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);
		
		fragment_view = findViewById(R.id.fragment_sidebar);
		if (fragment_view != null)
		{
			fragment_view.setVisibility(View.GONE);
			fragment_view.findViewById(android.R.id.list).setPadding(32, 0, 32, 0);
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == R.id.action_preferences)
		{
			if (fragment_view != null)
			{
				if (fragment_view.getVisibility() == View.VISIBLE)
				{
					fragment_view.setVisibility(View.GONE);
				}
				else
				{
					fragment_view.setVisibility(View.VISIBLE);
				}
				
				return true;
			}
			else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
			{
				Intent intent = new Intent(this, RDPreferenceFragmentActivity.class);
				startActivity(intent);
			}
			else
			{
				// FIXME: remove when dropping Gingerbread support sometime
				Intent intent = new Intent(this, RDPreferenceActivity.class);
				startActivity(intent);
			}
			
		}
		return super.onOptionsItemSelected(item);
	}
}
