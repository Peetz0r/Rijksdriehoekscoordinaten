package nl.haas_en_berg.rijksdriehoekscoordinaten;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;

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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

public class RDFragment extends Fragment
{
	private MapView mapView = null;
	private GoogleMap googleMap = null;
	private EditText RD_lon = null;
	private EditText RD_lat = null;
	private CoordinateTransform wgs84_to_rd = null;
	private CoordinateTransform rd_to_wgs84 = null;
	private SharedPreferences prefs = null;
	private Spinner spinner = null;
	private ArrayAdapter<CharSequence> adapter = null;
	
	// private DecimalFormat decimalFormatter = new DecimalFormat("#.00");
	// private double formatMultiplier = 1000;
	
	// private DecimalFormat decimalFormatter = new DecimalFormat("#");
	// private double formatMultiplier = 1;
	
	private DecimalFormat decimalFormatter = null;
	private double formatMultiplier = 0;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		View layoutView = inflater.inflate(R.layout.fragment_rd, container, false);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		prefs.registerOnSharedPreferenceChangeListener(prefsListener);
		
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
				
				// populate the map type spinner
				spinner = (Spinner) layoutView.findViewById(R.id.spinner);
				adapter = ArrayAdapter.createFromResource(getActivity(), R.array.pref_map_type_entries, android.R.layout.simple_spinner_item);
				adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
				spinner.setAdapter(adapter);
				spinner.setOnItemSelectedListener(spinnerListener);
				
				// get the default map type from the settings, and set in on the
				// map and also set the position for the spinner.
				String map_type = prefs.getString("pref_map_type", "");
				mapTypeUpdate(map_type);
				
				// get our 'last known' location
				LocationManager lm = (LocationManager) getActivity().getSystemService(FragmentActivity.LOCATION_SERVICE);
				Location myLocation = lm.getLastKnownLocation(lm.getBestProvider(new Criteria(), true));
				
				// myLocation can be null in all sorts of cases (cold boot,
				// location stuff disabled by user, etc...)
				if (myLocation != null)
				{
					LatLng myLatLng = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
					googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 14));
				}
				else
				{
					// when there's not (yet) a myLocation available, then just
					// set our start position to the OLV-toren because that's
					// where the RD grid started after all
					googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(52.1551723, 5.38720358), 14));
				}
				
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
			Toast.makeText(getActivity(), "Hey! " + key, Toast.LENGTH_LONG).show();
			
			formatUpdate();
			cameraListener.onCameraChange(googleMap.getCameraPosition());
			
			String map_type = prefs.getString("pref_map_type", "");
			mapTypeUpdate(map_type);
		}
	};
	
	private void mapTypeUpdate(String map_type)
	{
		if (map_type.equals("Luchtfoto"))
		{
			googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
			spinner.setSelection(adapter.getPosition(map_type));
		}
		else if (map_type.equals("Topografisch"))
		{
			googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
			spinner.setSelection(adapter.getPosition(map_type));
		}
		else
		{
			googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
			spinner.setSelection(0);
		}
	}
	
	private OnItemSelectedListener spinnerListener = new OnItemSelectedListener()
	{
		public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id)
		{
			mapTypeUpdate(parent.getItemAtPosition(pos).toString());
		}
		
		@Override
		public void onNothingSelected(AdapterView<?> arg0)
		{
		};
	};
	
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
