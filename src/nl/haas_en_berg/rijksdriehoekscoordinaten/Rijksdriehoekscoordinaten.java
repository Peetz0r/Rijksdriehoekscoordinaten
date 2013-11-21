package nl.haas_en_berg.rijksdriehoekscoordinaten;

import java.text.DecimalFormat;
import java.text.ParseException;

import org.osgeo.proj4j.CRSFactory;
import org.osgeo.proj4j.CoordinateReferenceSystem;
import org.osgeo.proj4j.CoordinateTransform;
import org.osgeo.proj4j.CoordinateTransformFactory;
import org.osgeo.proj4j.ProjCoordinate;

import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

public class Rijksdriehoekscoordinaten extends Activity
{
	private MapView mapView = null;
	private GoogleMap googleMap = null;
	private EditText RD_lon = null;
	private EditText RD_lat = null;
	private DecimalFormat decimalFormatter = new DecimalFormat("#.00");
	private CoordinateTransform wgs84_to_rd = null;
	private CoordinateTransform rd_to_wgs84 = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_rijksdriehoekscoordinaten);
		
		// find the MapView
		mapView = (MapView) findViewById(R.id.map);
		mapView.onCreate(savedInstanceState);
		
		// find the EditTexts
		RD_lon = (EditText) findViewById(R.id.RD_lon);
		RD_lat = (EditText) findViewById(R.id.RD_lat);
		
		// change the background to semi-transparent white without actually
		// chaging the background drawable
		RD_lon.getBackground().setColorFilter(Color.argb(160, 255, 255, 255), Mode.MULTIPLY);
		RD_lat.getBackground().setColorFilter(Color.argb(160, 255, 255, 255), Mode.MULTIPLY);
		
		// check if this device does have the Google Play Services (required for
		// the Maps API)
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if (resultCode != ConnectionResult.SUCCESS)
		{
			// Oops, we don't have the Google Play services on this device.
			// You are not going to space today...
			Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
			if (dialog != null)
			{
				// replace the layout with am empty View, so that we have an
				// empty background behind the dialog
				setContentView(new View(this));
				
				// wait for the user to (hopefully read, and) dismiss the
				// dialog, then finish the activity (and exit our app)
				OnDismissListener listener = new OnDismissListener()
				{
					@Override
					public void onDismiss(DialogInterface dialog)
					{
						finish();
					}
				};
				
				// we exit our activity after showing the dialog,
				// since there's nothing the user can do anyway
				dialog.setOnDismissListener(listener);
				dialog.show();
			}
			else
			{
				Toast.makeText(getApplicationContext(), "Er gaat iets mis.", Toast.LENGTH_LONG).show();
				finish();
			}
		}
		else
		{
			// get the GoogleMap
			googleMap = mapView.getMap();
			
			// double-check if this is successful
			if (googleMap == null)
			{
				Toast.makeText(getApplicationContext(), "Er gaat iets mis.", Toast.LENGTH_LONG).show();
				finish();
			}
			else
			{
				
				// this is needed for some weird undocumented reason
				try
				{
					MapsInitializer.initialize(this);
				}
				catch (GooglePlayServicesNotAvailableException e)
				{
					e.printStackTrace();
				}
				
				// set our preferences for the map
				googleMap.setMyLocationEnabled(true);
				googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
				googleMap.setOnCameraChangeListener(cameraListener);
				googleMap.getUiSettings().setTiltGesturesEnabled(false);
				googleMap.getUiSettings().setRotateGesturesEnabled(false);
				
				// populate out map type spinner
				Spinner spinner = (Spinner) findViewById(R.id.spinner);
				CharSequence[] list = { "Kaart", "Luchtfoto", "Topografisch" };
				ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item, list);
				adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
				spinner.setAdapter(adapter);
				spinner.setOnItemSelectedListener(spinnerListener);
				
				// get our 'last known' location
				LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
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
					googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(52.15514, 5.38718), 14));
				}
				
				// prepare the two CoordinateTransform objects to do the magic
				// later on :)
				CRSFactory csFactory = new CRSFactory();
				CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
				
				CoordinateReferenceSystem crs_rd = csFactory.createFromParameters("EPSG:28992", "+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889 +k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel +units=m +towgs84=565.2369,50.0087,465.658,-0.406857330322398,0.350732676542563,-1.8703473836068,4.0812 +no_defs");
				CoordinateReferenceSystem crs_wgs84 = csFactory.createFromParameters("WGS84", "+title=long/lat:WGS84 +proj=longlat +ellps=WGS84 +datum=WGS84 +units=degrees");
				
				wgs84_to_rd = ctFactory.createTransform(crs_wgs84, crs_rd);
				rd_to_wgs84 = ctFactory.createTransform(crs_rd, crs_wgs84);
			}
		}
	}
	
	private OnItemSelectedListener spinnerListener = new OnItemSelectedListener()
	{
		public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id)
		{
			// we just compare the itemAtPosition to the actual strings there,
			// and set the map type accordingly
			if (parent.getItemAtPosition(pos).toString().equals("Luchtfoto"))
			{
				googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
			}
			else if (parent.getItemAtPosition(pos).toString().equals("Topografisch"))
			{
				googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
			}
			else
			{
				googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
			}
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
			// disable the listener on the EditTexts
			RD_lon.removeTextChangedListener(textListener);
			RD_lat.removeTextChangedListener(textListener);
			
			// get the position from the Google Map
			double lon_wgs84 = cameraPosition.target.longitude;
			double lat_wgs84 = cameraPosition.target.latitude;
			
			// do the Proj4j magic
			ProjCoordinate wgs84 = new ProjCoordinate(lon_wgs84, lat_wgs84);
			ProjCoordinate rd = new ProjCoordinate();
			wgs84_to_rd.transform(wgs84, rd);
			
			// we convert/round/format '148123' to '148,12'
			double lon_rd = rd.x / 1000;
			double lat_rd = rd.y / 1000;
			
			// we use setTextKeepState to prevent the users cursor from going
			// foobar while he/she is typing
			RD_lon.setTextKeepState(decimalFormatter.format(lon_rd));
			RD_lat.setTextKeepState(decimalFormatter.format(lat_rd));
			
			RD_lon.addTextChangedListener(textListener);
			RD_lat.addTextChangedListener(textListener);
		}
	};
	
	private TextWatcher textListener = new TextWatcher()
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
				RD_lon.removeTextChangedListener(textListener);
				RD_lat.removeTextChangedListener(textListener);
				
				// replace 'wrong' decimal separators
				char sep = decimalFormatter.getDecimalFormatSymbols().getDecimalSeparator();
				RD_lon.setTextKeepState(RD_lon.getText().toString().replace('.', sep));
				RD_lon.setTextKeepState(RD_lon.getText().toString().replace(',', sep));
				RD_lat.setTextKeepState(RD_lat.getText().toString().replace('.', sep));
				RD_lat.setTextKeepState(RD_lat.getText().toString().replace(',', sep));
				
				// re-enable the listener
				RD_lon.addTextChangedListener(textListener);
				RD_lat.addTextChangedListener(textListener);
				
				// get and parse the coordinates from the EditTexts
				double lon_rd = decimalFormatter.parse(RD_lon.getText().toString()).doubleValue();
				double lat_rd = decimalFormatter.parse(RD_lat.getText().toString()).doubleValue();
				
				// check if the coordinates entered are complete
				boolean lon_ready = decimalFormatter.format(lon_rd).equals(RD_lon.getText().toString());
				boolean lat_ready = decimalFormatter.format(lat_rd).equals(RD_lat.getText().toString());
				
				if (lon_ready && lat_ready)
				{
					// do the Proj4j magic
					ProjCoordinate rd = new ProjCoordinate(lon_rd * 1000, lat_rd * 1000);
					ProjCoordinate wgs84 = new ProjCoordinate();
					rd_to_wgs84.transform(rd, wgs84);
					
					// move the map to the new coordinates
					googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(wgs84.y, wgs84.x)));
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
	protected void onResume()
	{
		super.onResume();
		mapView.onResume();
	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		mapView.onDestroy();
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		mapView.onPause();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState)
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
