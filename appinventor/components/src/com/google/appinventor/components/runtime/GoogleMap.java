package com.google.appinventor.components.runtime;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import android.R;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.*;
import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.ComponentConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.*;
import com.google.gson.*;
import org.json.JSONException;


/** Component for displaying information on Google Map
 * This component makes use of Android MapView (v2) to location specific information.
 * App Inventor user could use this component to do things like those demo apps
 * for Google Mapview in the android sdk
 *
 * @author fuming@mit.mit (Fuming Shih)
 */
@DesignerComponent(version = YaVersion.GOOGLE_MAP_COMPONENT_VERSION,
    description = "Visible component that show information on Google map.",
    category = ComponentCategory.MISC)
@SimpleObject
@UsesPermissions(permissionNames = "android.permission.WRITE_EXTERNAL_STORAGE, "
    + "android.permission.ACCESS_NETWORK_STATE, "
    + "android.permission.INTERNET, "
    + "android.permission.ACCESS_COARSE_LOCATION, "
    + "android.permission.ACCESS_FINE_LOCATION, "
    + "com.google.android.providers.gsf.permission.READ_GSERVICES, "
    + "android.permission.WRITE_EXTERNAL_STORAGE")
@UsesLibraries(libraries = "google-play-services.jar")
public class GoogleMap extends AndroidViewComponent implements OnResumeListener, OnInitializeListener,
OnMarkerClickListener, OnInfoWindowClickListener, OnMarkerDragListener, OnMapClickListener, 
OnMapLongClickListener, OnCameraChangeListener{

  private final Activity context;
  private final Form form;
  private static final String TAG = "GoogleMap";

  // Layout
  // We create thie LinerLayout and add our mapFragment in it.
  private final com.google.appinventor.components.runtime.LinearLayout viewLayout;


  // translates App Inventor alignment codes to Android gravity
  private final AlignmentUtil alignmentSetter;

  // the alignment for this component's LinearLayout
  private int verticalAlignment;

  private static final String MAP_FRAGMENT_TAG = "map";
  private com.google.android.gms.maps.GoogleMap mMap;
  private SupportMapFragment mMapFragment;

  private HashMap<Integer, Marker> markers = new HashMap<Integer, Marker>();

  //basic configurations of a map
  private int mapType = com.google.android.gms.maps.GoogleMap.MAP_TYPE_NORMAL;
  private boolean myLocationEnabled = false;
  private boolean compassEnabled = false;
  private boolean rotateEnabled = true;
  private boolean scrollEnabled = true;
  private boolean zoomControlEnabled = false;
  private boolean zoomGesturesEnabled = true;

  // default settings for marker
  private int mMarkerColor =  Component.COLOR_BLUE;
  private boolean mMarkerDraggable = false;

  // settings for map event listener
  private boolean enableMapClickListener = false;
  private boolean enableMapLongClickListener = false;
  private boolean enableCameraChangeListener = false;

  // setting up for cicle overlay
  private List<DraggableCircle> mCircles = new ArrayList<DraggableCircle>(1);
  private static final double DEFAULT_RADIUS = 1000000;
  public static final double RADIUS_OF_EARTH_METERS = 6371009;

  private float mStrokeWidth = 10; // in pixel, 0 means no outline will be drawn
  private int mStrokeColor = Color.BLACK;  // perimeter default color is black
  private int mColorHue = 0 ; // value ranges from [0, 360]
  private int mAlpha = 20; //min 0, default 127, max 255
  private int mFillColor =  Color.HSVToColor(mAlpha, new float[] {mColorHue, 1, 1});//default to red, medium level hue color


  private UiSettings mUiSettings;

  private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);
  private static final AtomicInteger snextMarkerId = new AtomicInteger(1);


  public GoogleMap(ComponentContainer container) throws IOException {
    super(container);
    context = container.$context();
    form = container.$form();
    viewLayout = new com.google.appinventor.components.runtime.LinearLayout(context,
      ComponentConstants.LAYOUT_ORIENTATION_VERTICAL);
    alignmentSetter = new AlignmentUtil(viewLayout);

    verticalAlignment = ComponentConstants.VERTICAL_ALIGNMENT_DEFAULT;

    alignmentSetter.setVerticalAlignment(verticalAlignment);
    ViewGroup viewG = viewLayout.getLayoutManager();

    viewG.setId(generateViewId());


    container.$add(this);           // add first (will be WRAP_CONTENT)
    Log.i(TAG, "here before reset width and length");
    container.setChildWidth(this, LENGTH_FILL_PARENT); //change to FILL_PARENT
    container.setChildHeight(this, LENGTH_FILL_PARENT);

    //add check if the phone has installed Google Map and Google Play Service sdk

    checkGooglePlayServiceSDK() ;
    checkGoogleMapInstalled() ;

    mMapFragment = (SupportMapFragment) form.getSupportFragmentManager()
        .findFragmentByTag(MAP_FRAGMENT_TAG);


    // We only create a fragment if it doesn't already exist.
    if (mMapFragment == null) {
//      // To programmatically add the map, we first create a SupportMapFragment.
      mMapFragment = SupportMapFragment.newInstance();

      //mMapFragment = new MySupportMapFragment();
      FragmentTransaction fragmentTransaction =
          form.getSupportFragmentManager().beginTransaction();
      Log.i(TAG, "here before adding fragment");
//      fragmentTransaction.add(viewG.getId(), mMapFragment, MAP_FRAGMENT_TAG);

      fragmentTransaction.add(android.R.id.content, mMapFragment, MAP_FRAGMENT_TAG);
      fragmentTransaction.commit();


    }

    // We can't be guaranteed that the map is available because Google Play services might
    // not be available.
    // setUpMapIfNeeded();
    form.registerForOnInitialize(this);
    form.registerForOnResume(this);

  }

//  /*
//   * Currently this is not working, we will come back to this later....
//   */
//
//      public class MySupportMapFragment extends SupportMapFragment {
//        public MySupportMapFragment() {
//          return;
//        }
//        @Override
//        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//          Log.v(TAG, "In overridden onCreateView.");
//          View v = super.onCreateView(inflater, container, savedInstanceState);
//
//      Log.v(TAG, "Initialising map.");
//
//      initMap();
//
//
//      return v;
//    }
//
//    @Override
//    public void onViewCreated (View view, Bundle savedInstanceState) {
//      super.onViewCreated(view, savedInstanceState);
//      Log.v(TAG, "chage to fill_parent.");
//
//      changeWidthHeight();
//      view.requestLayout();
////      vg.requestLayout();
//      Log.v(TAG, "Moving the MyPositionButton");
////      resetMyPositionButton(view);
//    }
//
//    private void initMap(){
//      UiSettings settings = getMap().getUiSettings();
//      settings.setAllGesturesEnabled(true);
//      settings.setMyLocationButtonEnabled(true);
//      LatLng latLong = new LatLng(22.320542, 114.185715);
//      getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(latLong,11));
//
//    }
//
//  }

  /**
   * Generate a value suitable for use in .
   * This value will not collide with ID values generated at build time by aapt for R.id.
   *
   * @return a generated ID value
   */
  private static int generateViewId() {
      for (;;) {
          final int result = sNextGeneratedId.get();
          // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
          int newValue = result + 1;
          if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
          if (sNextGeneratedId.compareAndSet(result, newValue)) {
              return result;
          }
      }
  }

  
//  Currently this doesn't work
//  @Override
//  @SimpleProperty()
//  public void Width(int width) {
//    if (width == LENGTH_PREFERRED) {
//      width = LENGTH_FILL_PARENT;
//    }
//    super.Width(width);
//  }
//
//  @Override
//  @SimpleProperty()
//  public void Height(int height) {
//    if (height == LENGTH_PREFERRED) {
//      height = LENGTH_FILL_PARENT;
//    }
//    super.Height(height);
//  }

  private void setUpMapIfNeeded() {
      // Do a null check to confirm that we have not already instantiated the map.
      if (mMap == null) {
        // Try to obtain the map from the SupportMapFragment.
        mMap = mMapFragment.getMap();
        // Check if we were successful in obtaining the map.
        if (mMap != null) {
          Log.i(TAG, "Yes, we have a google map...");
          setUpMap();
        } else {
          // means that Google Service is not available
          form.dispatchErrorOccurredEvent(this, "setUpMapIfNeeded",
              ErrorMessages.ERROR_GOOGLE_PLAY_NOTINSTALLED);
        }

      }
  }
  
  
  private void setUpMap() {
    // could be the boilerplate for initiating everything
    // including all the configurations and markers 
    
    // (testing: add an marker)
    
    // Set listeners for marker events.  See the bottom of this class for their behavior.
    mMap.setOnMarkerClickListener(this);
    mMap.setOnInfoWindowClickListener(this);
    mMap.setOnMarkerDragListener(this);
    
    //just for testing
    mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));

    // create UiSetting instance and default ui settings of the map
    mUiSettings = mMap.getUiSettings();
    mUiSettings.setCompassEnabled(this.compassEnabled);
    mUiSettings.setRotateGesturesEnabled(this.rotateEnabled);
    mUiSettings.setScrollGesturesEnabled(this.scrollEnabled);
    mUiSettings.setZoomControlsEnabled(this.zoomControlEnabled);
    mUiSettings.setZoomGesturesEnabled(this.zoomGesturesEnabled);

    // after this method is called, user can add markers and change other settings.
    MapIsReady();

  }

  //// below are many setters and getters for map UI settings
//  private boolean compassEnabled = false;
//  private boolean rotateEnabled = true;
//  private boolean scrollEnabled = true;
//  private boolean zoomControlEnabled = false;
//  private boolean zoomGesturesEnabled = true;
  ///

  @SimpleFunction (description = "Enables/disables the compass widget on the map's ui. Call this only after " +
      "event \"MapIsReady\" is received")
  public void EnableCompass(boolean enable) {
    this.compassEnabled = enable;
    mUiSettings.setCompassEnabled(enable);
  }

  @SimpleProperty (description = "Indicates whether the compass widget is currently enabled in the map ui")
  public boolean CompassEnabled() {
    return mUiSettings.isCompassEnabled();
  }

  @SimpleFunction (description = "Enables/disables the capability to rotate a map on the ui. Call this only after " +
      "the event \"MapIsReady\" is received.")
  public void EnableRotate(boolean enable) {
    this.rotateEnabled = enable;
    mUiSettings.setRotateGesturesEnabled(enable);
  }

  @SimpleProperty (description = "Indicates whether the capability to rotate a map on the ui is currently enabled")
  public boolean RotateEnabled() {
    return mUiSettings.isRotateGesturesEnabled();
  }

  @SimpleFunction (description = "Enables/disables the capability to scroll a map on the ui. Call this only after the " +
      "event \"MapIsReady\" is received")
  public void EnableScroll(boolean enable) {
    this.scrollEnabled = enable;
    mUiSettings.setScrollGesturesEnabled(enable);

  }

  @SimpleProperty (description = "Indicates whether the capability to scroll a map on the ui is currently enabled")
  public boolean ScrollEnabled() {
    return mUiSettings.isScrollGesturesEnabled();
  }

  @SimpleFunction (description = "Enables/disables the zoom widget on the map's ui. Call this only after the event" +
      " \"MapIsReady\" is received")
  public void EnableZoomControl(boolean enable) {
    this.zoomControlEnabled = enable;
    mUiSettings.setZoomControlsEnabled(enable);

  }

  @SimpleProperty (description = "Indicates whether the zoom widget on the map ui is currently enabled")
  public boolean ZoomControlEnabled() {
    return mUiSettings.isZoomControlsEnabled();
  }

  @SimpleFunction (description = "Enables/disables zoom gesture on the map ui. Call this only after the event " +
      " \"MapIsReady\" is received. ")
  public void EnableZoomGesture(boolean enable) {
    this.zoomGesturesEnabled = enable;
    mUiSettings.setZoomGesturesEnabled(enable);

  }

  @SimpleProperty (description = "Indicates whether the zoom gesture is currently enabled")
  public boolean ZoomGestureEnabled() {
    return mUiSettings.isZoomGesturesEnabled();
  }



  @SimpleEvent(description = "Indicates that the map has been rendered and ready for adding markers " +
      "or changing other settings. Please add or updating markers within this event")
  public void MapIsReady(){
    context.runOnUiThread(new Runnable() {
      public void run() {
        Log.i(TAG, "Map is ready for adding markers and other setting");
        EventDispatcher.dispatchEvent(GoogleMap.this, "MapIsReady");
      }
    });

  }


  private void checkGooglePlayServiceSDK() {
    //To change body of created methods use File | Settings | File Templates.
    final int googlePlayServicesAvailable = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
    if(googlePlayServicesAvailable != ConnectionResult.SUCCESS){
      form.dispatchErrorOccurredEvent(this, "checkGooglePlayServiceSDK",
          ErrorMessages.ERROR_GOOGLE_PLAY_NOTINSTALLED);
    }
  }

  private void checkGoogleMapInstalled() {
    try
    {
      ApplicationInfo info = context.getPackageManager().getApplicationInfo("com.google.android.apps.maps", 0 );

    }
    catch(PackageManager.NameNotFoundException e)
    {
      form.dispatchErrorOccurredEvent(this, "checkGoogleMapInstalled",
          ErrorMessages.ERROR_GOOGLE_MAP_NOTINSTALLED);
    }
  }

  /**
   *
   * @param lat Latitude of the center of the circle
   * @param lng Longitude of the center of the circle
   * @param radius Radius of the circle
   * @param alpha Alpha value of the color of the circle overlay
   * @param hue Hue value of the color of the circle overaly
   */
  @SimpleFunction (description = "Create a circle overlay on the map UI with specified center and radius. " +
      "Returning a unique id of the circle for future reference to events raised by moving this circle. If the circle is" +
      "set to be draggable, two default markers will appear on the map: one in the center of the circle, another on the perimeter.")
  public int AddCircleOverlay(double lat, double lng, double radius, int alpha, float hue, boolean draggable){


    return 0;

  }

  @SimpleEvent(description = "Event been raised after the action of moving a circle overlay is finished. If previously set" +
      "the circle to be draggable")
  public void FinishedDraggingCircle() {


  }






  // AndroidViewComponent implementation

  @Override
  public View getView() {
    return viewLayout.getLayoutManager();
  }

  @Override
  public void onResume() {
    // TODO: http://stackoverflow.com/questions/15001207/android-googlemap-is-null-displays-fine-but-cant-add-markers-polylines
    // only now is it saved to redraw the map...
    Log.i(TAG, "in onResume...Google Map redraw");
    setUpMapIfNeeded();

  }

  @Override
  public void onInitialize() {
    // TODO Auto-generated method stub
    Log.i(TAG, "try to do after the component is initialized...");
    setUpMapIfNeeded();
    // fire an event so that AI user could add markers on initialized 

  }

    @SimpleFunction(description = "Enable or disable my location widget for Google Map")
    public void EnableMyLocation(boolean enabled){
      if (this.myLocationEnabled != enabled)
        this.myLocationEnabled = enabled;

      if (mMap != null) {
        mMap.setMyLocationEnabled(myLocationEnabled);
      }

  }
  @SimpleProperty(description = "Indicates whether my locaiton widget is currently enabled for the Google map")
  public boolean MyLocationEnabled(){
    return this.myLocationEnabled;
  }


  @SimpleFunction(description = "Set the layer of Google map. Default layer is \"normal\", other choices including \"hybrid\"," +
      "\"satellite\", and \"terrain\" ")
  public void SetMapType(String layerName){
    
    if (layerName.equals("normal")) {
      this.mapType = com.google.android.gms.maps.GoogleMap.MAP_TYPE_NORMAL;
    } else if (layerName.equals("hybrid")) {
      this.mapType = com.google.android.gms.maps.GoogleMap.MAP_TYPE_HYBRID;
    } else if (layerName.equals("satellite")) {
      this.mapType = com.google.android.gms.maps.GoogleMap.MAP_TYPE_SATELLITE;
    } else if (layerName.equals("terrain")) {
      this.mapType = com.google.android.gms.maps.GoogleMap.MAP_TYPE_TERRAIN;
    } else {  
      Log.i(TAG, "Error setting layer with name " + layerName);
      form.dispatchErrorOccurredEvent(this, "SetMapType",
          ErrorMessages.ERROR_GOOGLE_MAP_INVALID_INPUT);
    }

    if(mMap != null) {
      mMap.setMapType(this.mapType);
    }


  }

  /**
   * Enable map click event listener for this component
   * @param enabled
   */
  @SimpleFunction(description = "Enable/Disable to listen to map's click event")
  public void EnableMapClickListener(boolean enabled) {
    if (this.enableMapClickListener != enabled)
      this.enableMapClickListener = enabled;

    if (mMap != null) {
      mMap.setOnMapClickListener(enabled? this : null);

    }

  }

  /**
   * Indicates if the mapClick listener is currently enabled
   * @return
   */
  @SimpleProperty (description = "Indicates if the mapClick event listener is currently enabled")
  public boolean MapClickListenerEnabled() {
    return this.enableMapClickListener;
  }

  /**
   * Enable map long click event listener
   * @return
   */
  @SimpleFunction (description = "Enable/disable to listen to map's long click event")
  public void EnableMapLongClickListener(boolean enabled){
    if (this.enableMapLongClickListener != enabled) {
      this.enableMapLongClickListener = enabled;
    }
    if (mMap != null) {
      mMap.setOnMapLongClickListener(enabled? this : null);
    }
  }

  /**
   * Indicates if the map's longClick event listener is currently enabled
   * @return
   */
  @SimpleProperty (description = "Indicates if the map longClick listener is currently enabled")
  public boolean MapLongClickListenerEnabled() {
    return this.enableMapLongClickListener;
  }

  /**
   * Enable/Disable map's camera position changed event
   * @param enabled
   */
  @SimpleFunction (description = "Enable/Disable to listen to map's camera position changed event")
  public void EnableMapCameraPosChangeListener(boolean enabled){
    if (this.enableCameraChangeListener != enabled) {
      this.enableCameraChangeListener = enabled;

    }
    if (mMap != null) {
      mMap.setOnCameraChangeListener(enabled? this : null);
    }

  }

  /**
   * Indicates if the map camera's position changed listener is currently enabled
   * @return
   */
  @SimpleProperty (description = "Indicates if the map camera's position changed listener is currently enabled")
  public boolean MapCameraChangedListenerEnabled() {
    return this.enableCameraChangeListener;
  }


  @SimpleProperty(description = "Indicates the current map type")
  public String MapType(){
    switch(this.mapType){
      case com.google.android.gms.maps.GoogleMap.MAP_TYPE_NORMAL:
        return "normal";
      case com.google.android.gms.maps.GoogleMap.MAP_TYPE_HYBRID:
        return "hybrid";
      case com.google.android.gms.maps.GoogleMap.MAP_TYPE_SATELLITE:
        return "satellite";
      case com.google.android.gms.maps.GoogleMap.MAP_TYPE_TERRAIN:
        return "terrain";
 
    } 
    return null;

  }

  /**
   *
   * @param markers
   * @return
   * TODO: Adding customized icons
   */
  @SimpleFunction(description = "Adding a list of YailLists for markers. The representation of a maker in the " +
      "inner YailList is composed of: " +
      "lat(double) [required], long(double) [required], Color, " +
      "title(String), snippet(String), draggable(boolean). Return a list of unqiue ids for the added markers for future references")
  public YailList AddMarkers(YailList markers){
    // For color, check out the code in Form.java$BackgroundColor() e.g. if (argb != Component.COLOR_DEFAULT) 
    // After the color is chosen, it's passed in as int into the method
    // We can have two ways for supporting color of map markers: 1) pass it in as int in the Yailist, 
    // 2) if the user omit the value for color, we will use the blue color\
    // what's a easier way for people to know about the color list? 
    // App Inventor currently uses RGB (android.graphics.Color), but android map marker uses HUE
    // http://developer.android.com/reference/com/google/android/gms/maps/model/BitmapDescriptorFactory.html#HUE_YELLOW 
    // We can use Android.graphics.Color.colorToHSV(int rgbcolor, float[]hsv) to get the hue value in the hsv array
    float [] hsv = new float[3];
    ArrayList<Integer> markerIds = new ArrayList<Integer>();
    for (Object marker : markers.toArray()) {
      if (marker instanceof YailList) {
        if (((YailList) marker).size() < 2){
          // throw an exception with error messages
          form.dispatchErrorOccurredEvent(this, "AddMarkers",
              ErrorMessages.ERROR_GOOGLE_MAP_INVALID_INPUT);

        }
        Double lat = (Double) ((YailList) marker).get(0);
        Double lng = (Double) ((YailList) marker).get(1);

        int color = mMarkerColor;
        String title = "";
        String snippet = "";
        boolean draggable = mMarkerDraggable;

        if (((YailList) marker).size() >= 3){
          color = (Integer) ((YailList) marker).get(3);
        }
        if (((YailList) marker).size() >= 4){
          title = (String) ((YailList) marker).get(4);
        }
        if (((YailList) marker).size() >= 5){
           snippet = (String) ((YailList) marker).get(5);
        }
        if (((YailList) marker).size() >= 6) {
          draggable = (Boolean) ((YailList) marker).get(6);
        }


        Color.colorToHSV(color, hsv);
        int uniqueId = generateMarkerId();
        markerIds.add(uniqueId);
        addMarkerToMap(lat, lng, uniqueId, hsv[0], title, snippet, draggable);
      }
      else {
        // fire exception and throw error messages
        form.dispatchErrorOccurredEvent(this, "AddMarkers",
            ErrorMessages.ERROR_GOOGLE_MAP_INVALID_INPUT);

      }
    }
    return YailList.makeList(markerIds);
  
  }


  /**
   * generate unique marker id
   * @return
   */
  private static int generateMarkerId(){
    return snextMarkerId.incrementAndGet();

  }

  /**
   * Add a marker on the Google Map
   * @param lat
   * @param lng
   * @param title
   * @param snippet
   * @param hue
   */
  private void addMarkerToMap(Double lat, Double lng, int id, float hue, String title,
                               String snippet, boolean draggable) {
    // what if there are too many markers on Google Map ?
    // TODO: https://code.google.com/p/android-maps-extensions/
    LatLng latlng = new LatLng(lat, lng);
    Marker marker = mMap.addMarker(new MarkerOptions()
        .position(latlng)
        .icon(BitmapDescriptorFactory.defaultMarker(hue)));
    
    if (!title.isEmpty()){
      marker.setTitle(title);
    }
    if (!snippet.isEmpty()){
      marker.setSnippet(snippet);
    }
    marker.setDraggable(draggable);

    markers.put(id, marker);
  }

  @SimpleFunction(description = "Adding a list of markers that are represented as JsonArray. The inner JsonObject represents a marker" +
      "and is composed of name-value pairs. Name fields for a marker are \"lat\" (type double) [required], \"lng\"(type double) [required], " +
      "\"color\"(type int)[in hue value ranging from 0-360], " +
      "\"title\"(type String), \"snippet\"(type String), \"draggable\"(type boolean)")

  public YailList AddMarkersFromJson(String jsonString){
    ArrayList<Integer> markerIds = new ArrayList<Integer>();
    JsonParser parser = new JsonParser();
    // parse jsonString into jsonArray

    try {
      JsonElement markerList = parser.parse(jsonString);
      if (markerList.isJsonArray()){
        JsonArray markerArray = markerList.getAsJsonArray();
        for (JsonElement marker : markerArray){
          //now we have marker
          if(marker.isJsonObject()){
            JsonObject markerJson = marker.getAsJsonObject();
            if (markerJson.get("lat").equals(null) || markerJson.get("lng").equals(null)){
              form.dispatchErrorOccurredEvent(this, "AddMarkersFromJson",
                  ErrorMessages.ERROR_GOOGLE_MAP_INVALID_INPUT);
              return YailList.makeList(markerIds);

            } else { // having correct syntax of a marker in Json
              double latitude = markerJson.get("lat").getAsDouble();
              double longitude = markerJson.get("lng").getAsDouble();

              int color = (markerJson.get("color") == null) ? mMarkerColor : markerJson.get("color").getAsInt();
              String title = (markerJson.get("title") == null) ? "" : markerJson.get("title").getAsString();
              String snippet = (markerJson.get("snippet") == null) ? "" : markerJson.get("snippet").getAsString();;
              boolean draggable = (markerJson.get("draggable") == null) ? mMarkerDraggable : markerJson.get("draggable").getAsBoolean();
              int uniqueId = generateMarkerId();
              markerIds.add(uniqueId);
              addMarkerToMap(latitude, longitude, uniqueId, color, title, snippet, draggable);

            }

          } else { //not a JsonObject
            form.dispatchErrorOccurredEvent(this, "AddMarkersFromJson",
                ErrorMessages.ERROR_GOOGLE_MAP_INVALID_INPUT);
            return YailList.makeList(markerIds);
          }

        }

      } else { // not a JsonArray
        form.dispatchErrorOccurredEvent(this, "AddMarkersFromJson",
            ErrorMessages.ERROR_GOOGLE_MAP_INVALID_INPUT);
        return YailList.makeList(markerIds);
      }


    } catch (JsonSyntaxException e) {
      form.dispatchErrorOccurredEvent(this, "AddMarkersFromJson",
          ErrorMessages.ERROR_GOOGLE_MAP_JSON_FORMAT_DECODE_FAILED, jsonString);
      return YailList.makeList(markerIds);
    }


    return YailList.makeList(markerIds);
  }


  /**
   * Add a list of YailList to the map
   * @param markers
   */
  @SimpleFunction(description = "Adding a list of YailList for markers. The inner YailList represents a marker " +
      "and is composed of lat(Double) [required], long(Double) [required], color(int)[in hue value ranging from 0-360], " +
      "title(String), snippet(String), draggable(boolean). Return a list of unique ids for the markers that are added")
  public YailList AddMarkersHue(YailList markers){

    ArrayList<Integer> markerIds = new ArrayList<Integer>();

    for (Object marker : markers.toArray()) {
      if (marker instanceof YailList) {
        if (((YailList) marker).size() < 2){
          // throw an exception with error messages
          form.dispatchErrorOccurredEvent(this, "AddMarkers",
              ErrorMessages.ERROR_GOOGLE_MAP_INVALID_INPUT);
        }
        Double lat = (Double) ((YailList) marker).get(0);
        Double lng = (Double) ((YailList) marker).get(1);
        Integer uniqueId = generateMarkerId();
        float color = BitmapDescriptorFactory.HUE_BLUE;
        String title = "";
        String snippet = "";
        boolean draggable = mMarkerDraggable;

        if (((YailList) marker).size() >= 3){
          color = (Float) ((YailList) marker).get(3);

        }
        if (((YailList) marker).size() >= 4){
          title = (String) ((YailList) marker).get(4);
        }

        if (((YailList) marker).size() >= 5){
          snippet = (String) ((YailList) marker).get(5);
        }
        if (((YailList) marker).size() >= 6) {
          draggable = (Boolean) ((YailList) marker).get(6);
        }
        markerIds.add(uniqueId);
        addMarkerToMap(lat, lng, uniqueId,  color, title, snippet, draggable);
      }
      else {
        // fire exception and throw error messages
        form.dispatchErrorOccurredEvent(this, "AddMarkers",
            ErrorMessages.ERROR_GOOGLE_MAP_INVALID_INPUT);
      }
    }
    return YailList.makeList(markerIds);
    
  }

  @SimpleFunction(description = "Set the property of a marker, note that the marker has to be added first or else will " +
      "throw an exception! Properties include: \"color\"(in type float, hue value ranging from 0-360), \"title\" (in type String), " +
      "\"snippet\"(in type String), \"draggable\"(in type boolean).")
  public void UpdateMarker(int markerId, String propertyName, Object value){
    //we don't support update lat, lng here, one can remove the marker and add a new one
    Marker marker = getMarkerIfExisted(markerId);

    if(propertyName.equals("color")) {
      marker.setIcon(BitmapDescriptorFactory.defaultMarker((Float)value));
    }
    if(propertyName.equals("title")) {
      marker.setTitle((String)value);
    }
    if(propertyName.equals("snippet")) {
      marker.setSnippet((String)value);
    }
    if(propertyName.equals("draggable")) {
      marker.setDraggable((Boolean)value);
    }

  }

  private Marker getMarkerIfExisted(int markerId){
    Marker marker = markers.get(markerId);
    if(marker.equals(null)){
      form.dispatchErrorOccurredEvent(this, "UpdateMarker",
          ErrorMessages.ERROR_GOOGLE_MAP_MARKER_NOT_EXIST, Integer.toString(markerId));
    }
    return marker;
  }

  @SimpleFunction(description = "Remove a marker from the map")
  public void RemoveMarker(int markerId) {
    Marker marker = getMarkerIfExisted(markerId);
    marker.remove();
  }


  @Override
  public void onMarkerDrag(Marker marker) {
    // TODO Auto-generated method stub
    Integer markerId = getKeyByValue(markers, marker);
    LatLng latlng = marker.getPosition();
    OnMarkerDrag(markerId, latlng.latitude, latlng.longitude);

    
  }

  @Override
  public void onMarkerDragEnd(Marker marker) {
    // TODO Auto-generated method stub
    Integer markerId = getKeyByValue(markers, marker);
    LatLng latlng = marker.getPosition();
    OnMarkerDragEnd(markerId, latlng.latitude, latlng.longitude);
    
  }

  @Override
  public void onMarkerDragStart(Marker marker) {
    // TODO Auto-generated method stub
    Integer markerId = getKeyByValue(markers, marker);
    LatLng latLng = marker.getPosition();
    OnMarkerDragStart(markerId, latLng.latitude, latLng.longitude);

  }

  @SimpleEvent(description = "When a marker starts been dragged")
  public void OnMarkerDragStart (final int markerId, final double latitude, final double longitude){
    context.runOnUiThread(new Runnable() {
      public void run() {
        Log.i(TAG, "a marker:" + markerId + "starts been dragged");
        EventDispatcher.dispatchEvent(GoogleMap.this, "OnMarkerDragStart", markerId, latitude, longitude);
      }
    });


  }

  @SimpleEvent(description = "When a marker is been dragged")
  public void OnMarkerDrag (final int markerId, final double latitude, final double longitude){
    context.runOnUiThread(new Runnable() {
      public void run() {
        Log.i(TAG, "a marker:" + markerId + "is been dragged");
        EventDispatcher.dispatchEvent(GoogleMap.this, "OnMarkerDrag", markerId, latitude, longitude);
      }
    });

  }


  @SimpleEvent(description = "When the user drags a marker and finish the action, " +
      "returning marker's id and it's latest position")
  public void OnMarkerDragEnd(final int markerId, final double latitude, final double longitude){
    context.runOnUiThread(new Runnable() {
      public void run() {
        Log.i(TAG, "a marker:" + markerId + "finishes been dragged");
        EventDispatcher.dispatchEvent(GoogleMap.this, "OnMarkerDragEnd", markerId, latitude, longitude);
      }
    });


  }

  @SimpleEvent(description = "When a marker is clicked")
  public void OnMarkerClick(final int markerId, final double latitude, final double longitude){
    context.runOnUiThread(new Runnable() {
      public void run() {
        Log.i(TAG, "a marker:" + markerId + "is clicked");
        EventDispatcher.dispatchEvent(GoogleMap.this, "OnMarkerClick", markerId, latitude, longitude);
      }
    });

  }

  @SimpleEvent (description = "When the marker's infowindow is clicked, returning marker's id")
  public void InfoWindowClicked(final int markerId){
    context.runOnUiThread(new Runnable() {
      public void run() {
        Log.i(TAG, "A marker: " + markerId + " its info window is clicked");
        EventDispatcher.dispatchEvent(GoogleMap.this, "InfoWindowClicked", markerId);
      }
    });
  }
  
  @Override
  public void onInfoWindowClick(Marker marker) {
    // TODO Auto-generated method stub
    Integer markerId = getKeyByValue(markers, marker);
    InfoWindowClicked(markerId);

  }

  @Override
  public boolean onMarkerClick(Marker marker) {
    // TODO Auto-generated method stub
    Integer markerId = getKeyByValue(markers, marker);
    LatLng latLng = marker.getPosition();
    OnMarkerClick(markerId, latLng.latitude, latLng.longitude);

    // We return false to indicate that we have not consumed the event and that we wish
    // for the default behavior to occur (which is for the camera to move such that the
    // marker is centered and for the marker's info window to open, if it has one).
    return false;

  }


  /**
   * A small util function to get the key-value mapping in a map
   * @param map
   * @param value
   * @param <T>
   * @param <E>
   * @return
   */
  private <T, E> T getKeyByValue(Map<T, E> map, E value) {
    for (Map.Entry<T, E> entry : map.entrySet()) {
      if (value.equals(entry.getValue())) {
        return entry.getKey();
      }
    }
    return null;
  }

  @Override
  public void onCameraChange(CameraPosition position) {
    Double lat = position.target.latitude;
    Double lng = position.target.longitude;
    Float bearing = position.bearing;
    Float tilt = position.tilt;
    Float zoom = position.zoom;

    CameraPositionChanged(lat, lng, bearing, tilt, zoom);
    
  }

  /**
   * Called after the camera position has changed, returning all camera position parameters.
   * @param lat
   * @param lng
   * @param bearing
   * @param tilt
   * @param zoom
   */
  @SimpleEvent (description = "Called after the camera position of a map has changed.")
  public void CameraPositionChanged(double lat, double lng, float bearing, float tilt, float zoom){
    EventDispatcher.dispatchEvent(this, "CameraPositionChanged", lat, lng, bearing, tilt, zoom);

  }
  

  @Override
  public void onMapLongClick(LatLng latLng) {
    // TODO Auto-generated method stub
    OnMapLongClick(latLng.latitude, latLng.longitude);

    
  }

  /**
   * Called when the user makes a long-press gesture on the map
   * @param lat
   * @param lng
   */
  @SimpleEvent (description = "Called when the user makes a long-press gesture on the map")
  public void OnMapLongClick(double lat, double lng){
    EventDispatcher.dispatchEvent(this, "OnMapLongClick", lat, lng);

  }


  @Override
  public void onMapClick(LatLng latLng) {
    // TODO Auto-generated method stub
    OnMapClick(latLng.latitude, latLng.longitude);

  }

  @SimpleEvent(description =  "Called when the user makes a tap gesture on the map")
  public void OnMapClick(double lat, double lng){
    EventDispatcher.dispatchEvent(this, "OnMapClick", lat, lng);
  }


  // private class representing the circle overlay. Code copied from Google Example
  private class DraggableCircle {
    private final Marker centerMarker;
    private final Marker radiusMarker;
    private final Circle circle;
    private double radius;
    public DraggableCircle(LatLng center, double radius) {
      this.radius = radius;
      centerMarker = mMap.addMarker(new MarkerOptions()
          .position(center)
          .draggable(true));
      radiusMarker = mMap.addMarker(new MarkerOptions()
          .position(toRadiusLatLng(center, radius))
          .draggable(true)
          .icon(BitmapDescriptorFactory.defaultMarker(
              BitmapDescriptorFactory.HUE_AZURE)));
      circle = mMap.addCircle(new CircleOptions()
          .center(center)
          .radius(radius)
          .strokeWidth(mStrokeWidth)
          .strokeColor(mStrokeColor)
          .fillColor(mFillColor));
    }
    public DraggableCircle(LatLng center, LatLng radiusLatLng) {
      this.radius = toRadiusMeters(center, radiusLatLng);
      centerMarker = mMap.addMarker(new MarkerOptions()
          .position(center)
          .draggable(true));
      radiusMarker = mMap.addMarker(new MarkerOptions()
          .position(radiusLatLng)
          .draggable(true)
          .icon(BitmapDescriptorFactory.defaultMarker(
              BitmapDescriptorFactory.HUE_AZURE)));
      circle = mMap.addCircle(new CircleOptions()
          .center(center)
          .radius(radius)
          .strokeWidth(mStrokeWidth)
          .strokeColor(mStrokeColor)
          .fillColor(mFillColor));
    }
    public boolean onMarkerMoved(Marker marker) {
      if (marker.equals(centerMarker)) {
        circle.setCenter(marker.getPosition());
        radiusMarker.setPosition(toRadiusLatLng(marker.getPosition(), radius));
        return true;
      }
      if (marker.equals(radiusMarker)) {
        radius = toRadiusMeters(centerMarker.getPosition(), radiusMarker.getPosition());
        circle.setRadius(radius);
        return true;
      }
      return false;
    }
//    public void onStyleChange() {
//      circle.setStrokeWidth(mWidthBar.getProgress());
//      circle.setFillColor(mFillColor);
//      circle.setStrokeColor(mStrokeColor);
//    }
  }

  /** Generate LatLng of radius marker */
  private static LatLng toRadiusLatLng(LatLng center, double radius) {
    double radiusAngle = Math.toDegrees(radius / RADIUS_OF_EARTH_METERS) /
        Math.cos(Math.toRadians(center.latitude));
    return new LatLng(center.latitude, center.longitude + radiusAngle);
  }

  private static double toRadiusMeters(LatLng center, LatLng radius) {
    float[] result = new float[1];
    Location.distanceBetween(center.latitude, center.longitude,
        radius.latitude, radius.longitude, result);
    return result[0];
  }

}
