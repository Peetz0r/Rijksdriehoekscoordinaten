package nl.haas_en_berg.rijksdriehoekscoordinaten;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgeo.proj4j.CRSFactory;
import org.osgeo.proj4j.CoordinateReferenceSystem;
import org.osgeo.proj4j.CoordinateTransform;
import org.osgeo.proj4j.CoordinateTransformFactory;
import org.osgeo.proj4j.ProjCoordinate;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

public class RDMainFragment extends Fragment
{
	private MapView mapView = null;
	private GoogleMap googleMap = null;
	private EditText RD_lon = null;
	private EditText RD_lat = null;
	private CoordinateTransform wgs84_to_rd = null;
	private CoordinateTransform rd_to_wgs84 = null;
	private SharedPreferences prefs = null;
	private DecimalFormat decimalFormatter = null;
	private double formatMultiplier = 0;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		View layoutView = inflater.inflate(R.layout.main_fragment, container, false);
		
		// make the share button work
		setHasOptionsMenu(true);
		
		// get user preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		prefs.registerOnSharedPreferenceChangeListener(prefsListener);
		
		// set the DecimalFormatter and stuff using those preferences
		formatUpdate();
		
		// find the MapView
		mapView = (MapView) layoutView.findViewById(R.id.map);
		mapView.onCreate(savedInstanceState);
		
		// find the EditTexts
		RD_lon = (EditText) layoutView.findViewById(R.id.RD_lon);
		RD_lat = (EditText) layoutView.findViewById(R.id.RD_lat);
		
		// check if this device does have the Google Play Services (required for
		// the Maps API)
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity());
		if (resultCode != ConnectionResult.SUCCESS)
		{
			// Oops, we don't have the Google Play services on this device.
			// You are not going to space today...
			Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, getActivity(), 0);
			if (dialog != null)
			{
				// replace the layout with am empty View, so that we have an
				// empty background behind the dialog
				layoutView = null;
				
				// wait for the user to (hopefully read, and) dismiss the
				// dialog, then finish the activity (and exit our app)
				OnDismissListener listener = new OnDismissListener()
				{
					@Override
					public void onDismiss(DialogInterface dialog)
					{
						getActivity().finish();
					}
				};
				
				// we exit our activity after showing the dialog,
				// since there's nothing the user can do anyway
				dialog.setOnDismissListener(listener);
				dialog.show();
			}
			else
			{
				Toast.makeText(getActivity(), "Er gaat iets mis.", Toast.LENGTH_LONG).show();
				getActivity().finish();
			}
		}
		else
		{
			// get the GoogleMap
			googleMap = mapView.getMap();
			
			// double-check if this is successful
			if (googleMap == null)
			{
				Toast.makeText(getActivity(), "Er gaat iets mis.", Toast.LENGTH_LONG).show();
				getActivity().finish();
			}
			else
			{
				
				// this is needed for some weird undocumented reason
				try
				{
					MapsInitializer.initialize(getActivity());
				}
				catch (GooglePlayServicesNotAvailableException e)
				{
					e.printStackTrace();
				}
				
				// set our preferences for the map
				googleMap.setMyLocationEnabled(true);
				googleMap.setOnCameraChangeListener(cameraListener);
				googleMap.getUiSettings().setTiltGesturesEnabled(false);
				googleMap.getUiSettings().setRotateGesturesEnabled(false);
				
				// get the default map type from the settings,
				// and set in on the map
				mapTypeUpdate();
				
				Intent intent = getActivity().getIntent();
				
				// set m
				LatLng startPosition = null;
				float startZoom = 16;
				
				try
				{
					Log.i("Peetz0r", intent.getAction().toString());
					Log.i("Peetz0r", intent.getDataString().toString());
				}
				catch (Exception e)
				{
				}
				
				// try to get a position from the Activity's Intent
				if (intent.getAction().equals(Intent.ACTION_VIEW) || intent.getAction().equals(Intent.ACTION_SEND))
				{
					Uri uri = intent.getData();
					// geo:52.39494397309149,5.2931031212210655?q=52.39494397309149%2C5.2931031212210655(148595%20-%20489682)&z=16
					if (uri.getScheme().equals("geo"))
					{
						Pattern p = Pattern.compile("geo:(\\d+\\.\\d+),(\\d+\\.\\d+)(?:.*z=(\\d+))?");
						Matcher m = p.matcher(uri.toString());
						if (m.find())
						{
							try
							{
								startPosition = new LatLng(Double.valueOf(m.group(1)), Double.valueOf(m.group(2)));
							}
							catch (NumberFormatException e)
							{
								// This happens when other apps provide a
								// invalid geo: URI. Let's ignore that
							}
							
							// the z parameter in a geo: URI is optional,
							// therefore group(3) may be null
							String z = m.group(3);
							if (z != null)
							{
								try
								{
									startZoom = Float.valueOf(z);
								}
								catch (NumberFormatException e)
								{
									// This, again, happens when other apps
									// provide a invalid geo: URI. Let's, again,
									// ignore that
								}
							}
						}
					}
					else if (uri.getScheme().startsWith("http"))
					{
						Pattern p = Pattern.compile("loc:(\\d+\\.\\d+),(\\d+\\.\\d+)");
						Matcher m = p.matcher(uri.toString());
						if (m.find())
						{
							try
							{
								startPosition = new LatLng(Double.valueOf(m.group(1)), Double.valueOf(m.group(2)));
							}
							catch (NumberFormatException e)
							{
								// This happens when other apps provide a http
								// URI that I don't really understand
							}
						}
					}
				}
				
				// when failed, try to get the last known location
				if (startPosition == null)
				{
					LocationManager lm = (LocationManager) getActivity().getSystemService(FragmentActivity.LOCATION_SERVICE);
					Location myLocation = lm.getLastKnownLocation(lm.getBestProvider(new Criteria(), true));
					
					// myLocation can be null in all sorts of cases (cold boot,
					// location stuff disabled by user, etc...)
					if (myLocation != null)
					{
						startPosition = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
					}
				}
				
				// when failed again, then just set our start position to the
				// OLV-tower because that's where the RD grid started after all
				if (startPosition == null)
				{
					startPosition = new LatLng(52.1551723, 5.38720358);
				}
				
				// actually move the camera to the position that we just found
				googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startPosition, startZoom));
				
				// prepare the two CoordinateTransform objects to do the magic
				// later on :)
				CRSFactory csFactory = new CRSFactory();
				CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
				
				// http://oegeo.wordpress.com/2008/05/20/note-to-self-the-one-and-only-rd-projection-string/
				// http://spatialreference.org/ref/epsg/4326/proj4/
				CoordinateReferenceSystem crs_rd = csFactory.createFromParameters("EPSG:28992", "+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889 +k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel +units=m +towgs84=565.2369,50.0087,465.658,-0.406857330322398,0.350732676542563,-1.8703473836068,4.0812 +no_defs");
				CoordinateReferenceSystem crs_wgs84 = csFactory.createFromParameters("EPSG:4326", "+proj=longlat +ellps=WGS84 +datum=WGS84 +units=degrees");
				
				wgs84_to_rd = ctFactory.createTransform(crs_wgs84, crs_rd);
				rd_to_wgs84 = ctFactory.createTransform(crs_rd, crs_wgs84);
			}
		}
		return layoutView;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == R.id.action_share)
		{
			// get the position from the Google Map
			LatLng pos = googleMap.getCameraPosition().target;
			
			// do the Proj4j magic
			ProjCoordinate wgs84 = new ProjCoordinate(pos.longitude, pos.latitude);
			ProjCoordinate rd = new ProjCoordinate();
			wgs84_to_rd.transform(wgs84, rd);
			
			// set the label, according to the user preferences
			String label = decimalFormatter.format(rd.x / formatMultiplier);
			label += " - " + decimalFormatter.format(rd.y / formatMultiplier);
			
			// also prepare the wgs84 coordinates
			String latlon = pos.latitude + "," + pos.longitude;
			
			// and the zoomlevel (rounded)
			String zoom = String.format("%.0f", googleMap.getCameraPosition().zoom);
			
			// build the URI. it contains the wgs84 coordinates in the
			// beginning, and again as part of the q= parameter
			Uri uri = Uri.parse("geo:" + latlon + "?q=" + Uri.encode(latlon + "(" + label + ")") + "&z=" + zoom);
			
			// this gives the user a list of apps such as Google Maps
			Intent shareIntent = new Intent(android.content.Intent.ACTION_VIEW, uri);
			startActivity(shareIntent);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void formatUpdate()
	{
		String format = prefs.getString("pref_format", "");
		if (format.equals("Kadaster"))
		{
			decimalFormatter = new DecimalFormat("#");
			formatMultiplier = 1;
		}
		else
		{
			decimalFormatter = new DecimalFormat("#.00");
			formatMultiplier = 1000;
		}
	}
	
	private OnSharedPreferenceChangeListener prefsListener = new OnSharedPreferenceChangeListener()
	{
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
			formatUpdate();
			cameraListener.onCameraChange(googleMap.getCameraPosition());
			
			mapTypeUpdate();
		}
	};
	
	private void mapTypeUpdate()
	{
		String map_type = prefs.getString("pref_map_type", "");
		if (map_type.equals("Luchtfoto"))
		{
			googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
		}
		else if (map_type.equals("Topografisch"))
		{
			googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
		}
		else
		{
			googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
		}
	}
	
	private OnCameraChangeListener cameraListener = new OnCameraChangeListener()
	{
		
		@Override
		public void onCameraChange(CameraPosition cameraPosition)
		{
			
			// disable the listener on the textWatcher
			RD_lon.removeTextChangedListener(textWatcher);
			RD_lat.removeTextChangedListener(textWatcher);
			
			// get the position from the Google Map
			double lon_wgs84 = cameraPosition.target.longitude;
			double lat_wgs84 = cameraPosition.target.latitude;
			
			// do the Proj4j magic
			ProjCoordinate wgs84 = new ProjCoordinate(lon_wgs84, lat_wgs84);
			ProjCoordinate rd = new ProjCoordinate();
			wgs84_to_rd.transform(wgs84, rd);
			
			double lon_rd = rd.x / formatMultiplier;
			double lat_rd = rd.y / formatMultiplier;
			
			// we use setTextKeepState to prevent the users cursor from going
			// foobar while he/she is typing
			RD_lon.setTextKeepState(decimalFormatter.format(lon_rd));
			RD_lat.setTextKeepState(decimalFormatter.format(lat_rd));
			
			// re-enable the textWatcher
			RD_lon.addTextChangedListener(textWatcher);
			RD_lat.addTextChangedListener(textWatcher);
		}
	};
	
	private TextWatcher textWatcher = new TextWatcher()
	{
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count)
		{
		}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after)
		{
		}
		
		@Override
		public void afterTextChanged(Editable s)
		{
			try
			{
				// disable the listener
				RD_lon.removeTextChangedListener(textWatcher);
				RD_lat.removeTextChangedListener(textWatcher);
				
				// replace 'wrong' decimal separators
				char sep = decimalFormatter.getDecimalFormatSymbols().getDecimalSeparator();
				RD_lon.setTextKeepState(RD_lon.getText().toString().replace('.', sep));
				RD_lon.setTextKeepState(RD_lon.getText().toString().replace(',', sep));
				RD_lat.setTextKeepState(RD_lat.getText().toString().replace('.', sep));
				RD_lat.setTextKeepState(RD_lat.getText().toString().replace(',', sep));
				
				// re-enable the listener
				RD_lon.addTextChangedListener(textWatcher);
				RD_lat.addTextChangedListener(textWatcher);
				
				// get and parse the coordinates from the EditTexts
				double lon_rd = decimalFormatter.parse(RD_lon.getText().toString()).doubleValue();
				double lat_rd = decimalFormatter.parse(RD_lat.getText().toString()).doubleValue();
				
				// check if the coordinates entered are complete
				boolean lon_ready = decimalFormatter.format(lon_rd).equals(RD_lon.getText().toString());
				boolean lat_ready = decimalFormatter.format(lat_rd).equals(RD_lat.getText().toString());
				
				// multiply (convert meters to kilometers when needed)
				lon_rd *= formatMultiplier;
				lat_rd *= formatMultiplier;
				
				if (lon_ready && lat_ready)
				{
					// make sure the coordinates are actually within the bounds
					// of the RD grid
					if (lon_rd > 0 && lon_rd < 300000 && lat_rd > 300000 && lat_rd < 629000)
					{
						// do the Proj4j magic
						ProjCoordinate rd = new ProjCoordinate(lon_rd, lat_rd);
						ProjCoordinate wgs84 = new ProjCoordinate();
						rd_to_wgs84.transform(rd, wgs84);
						
						// move the map to the new coordinates
						googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(wgs84.y, wgs84.x)));
					}
				}
			}
			catch (ParseException e)
			{
				// This happens when parsing fails. We don't need to act on
				// this, just wait until the user fixed his/her own input
				// mistake.
			}
		}
	};
	
	@Override
	public void onResume()
	{
		super.onResume();
		mapView.onResume();
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mapView.onDestroy();
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		mapView.onPause();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		mapView.onSaveInstanceState(outState);
	}
	
	@Override
	public void onLowMemory()
	{
		super.onLowMemory();
		mapView.onLowMemory();
	}
	
}
