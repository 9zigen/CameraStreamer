package com.rpicopter.rpicamerastreamer;

import java.net.InetAddress;

import org.freedesktop.gstreamer.GStreamer;

import com.rpicopter.rpicamerastreamer.util.RPiComm;
import com.rpicopter.rpicamerastreamer.util.SystemUiHider;
import com.rpicopter.rpicamerastreamer.util.Utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class MainActivity extends Activity implements SurfaceHolder.Callback  {
    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeConfig(byte[] ip, int port);
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativePause();    // Set pipeline to PAUSED
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();
    private long native_custom_data;      // Native code will use this to keep private data
    
    private boolean is_playing_desired;   // Whether the user asked to go to PLAYING
    private boolean is_rpi_streaming;
    
    private RPiComm rpi;
	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = true;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
	 * user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = true;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		is_rpi_streaming = false;
		
        // Initialize GStreamer and warn if it fails
        try {
            GStreamer.init(this);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish(); 
            return;
        }
        
		setContentView(R.layout.activity_main);

		final TextView tv = (TextView) this.findViewById(R.id.textview_message);
		tv.setVisibility(View.INVISIBLE);
		final View controlsViewTop = findViewById(R.id.fullscreen_content_controls_top);
		final View controlsViewBottom = findViewById(R.id.fullscreen_content_controls_bottom);
		//final View contentView = findViewById(R.id.surface_video);
		
        SurfaceView sv = (SurfaceView) this.findViewById(R.id.surface_video);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);
        
        if (savedInstanceState != null) {
            is_playing_desired = savedInstanceState.getBoolean("playing");
            Log.i ("GStreamer", "Activity created. Saved state is playing:" + is_playing_desired);
        } else {
            is_playing_desired = true;
            Log.i ("GStreamer", "Activity created. There is no saved state, playing: "+is_playing_desired);
        }

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
        
  
		mSystemUiHider = SystemUiHider.getInstance(this, sv,
				HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider
				.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {

					@Override
					@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
					public void onVisibilityChange(boolean visible) {

							controlsViewTop.setVisibility(visible ? View.VISIBLE
									: View.GONE);
							controlsViewBottom.setVisibility(visible ? View.VISIBLE
									: View.GONE);
							
						if (visible && AUTO_HIDE) {
							// Schedule a hide().
							delayedHide(AUTO_HIDE_DELAY_MILLIS);
						}
					}
				});

		// Set up the user interaction to manually show or hide the system UI.
		sv.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
			}
		});

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
		findViewById(R.id.bottom_button).setOnTouchListener(
				mDelayHideTouchListener);
		
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		Editor editor = sharedPrefs.edit();
		
		editor.putString("my_ip", Utils.getIPAddress(true));
		
		if (sharedPrefs.getString("my_port", "")=="")
			editor.putString("my_port", "8888");
		
		//editor.putString("rpi_ip", "10.0.2.1");

		if (sharedPrefs.getString("rpi_port", "")=="")
			editor.putString("rpi_port", "1045");

		editor.commit();
		
		initializePlayer();
		nativeInit();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		delayedHide(100);
	}
	
	public void clickMsg(View v) {
		Log.d("CLICK","Msg");
	}
	
	public void clickOptions(View v) {
		  Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
          startActivityForResult(i, 1);
	}

	public void clickStart(View v) {
		if (rpi == null) {
			setMessage("Configuration error?");
			return;
		}
		if (is_rpi_streaming) { //we are currently playing
			rpi.stop();
			//nativeClose();
			is_rpi_streaming = false;
		} else { //we are currently not playing
			//nativeStart();
			rpi.start();
			is_rpi_streaming = true;
		}
		if (rpi.status == -1) {
			setMessage("Error: "+rpi.error);
			return;
		}
		refreshButton();
	}
	
    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
        final TextView tv = (TextView) this.findViewById(R.id.textview_message);
        tv.setVisibility(View.VISIBLE);
        runOnUiThread (new Runnable() {
          public void run() {
            tv.setText(message);
          }
        });
    }
    
    private void notifyState(final int state) {
    	Log.d("STATE","STATEA "+state);
    	switch (state) {
    		case 2: setMessage("PIPELINE READY"); is_rpi_streaming=false; break;
    		case 3: setMessage("PIPELINE AWAITING CONTENT"); is_rpi_streaming=false; break; //PAUSED
    		case 4: setMessage("PIPELINE ACTIVE"); is_rpi_streaming=true; break;
    		default: setMessage("PIPELINE STATE: "+state); break;
    	}
    	refreshButton();
    }
    
    private void initializePlayer() {
    	SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    	String my_ip_s = sharedPrefs.getString("my_ip", "");
    	String my_p_s = sharedPrefs.getString("my_port", "");
    	String rpi_ip_s = sharedPrefs.getString("rpi_ip", "");
    	String rpi_p_s = sharedPrefs.getString("rpi_port", "");
    	int my_p;
    	byte [] my_ip;
    	int rpi_p;
    	byte [] rpi_ip;
    	try{
    		my_p = Integer.parseInt(my_p_s);
    		InetAddress a = InetAddress.getByName(my_ip_s);
    		my_ip = a.getAddress();
    		rpi_p = Integer.parseInt(rpi_p_s);
    		InetAddress b = InetAddress.getByName(rpi_ip_s);
    		rpi_ip = b.getAddress();
    	} catch (Exception ex) {
    		setMessage("Check preferances for IP address and port!");
    		Log.d("initializePlayer","initializePlayer"+ex);
    		return;
    	}
    	nativeConfig(my_ip,my_p);
    	//Log.d("RPiComm","RPiComm0 "+rpi_ip[0]+" "+rpi_ip[1]+" "+rpi_ip[2]+" "+rpi_ip[3]);
    	if (rpi!=null) rpi.stop();
    	rpi = new RPiComm(rpi_ip,rpi_p,my_ip,my_p);
    }
        
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
                super.onActivityResult(requestCode, resultCode, data);

                    if(requestCode==1)
                    {
                    	Log.d("Settings","Returned 1");
                    	initializePlayer();       		
                    }

    }
	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the
	 * system UI. This is to prevent the jarring behavior of controls going away
	 * while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			if (AUTO_HIDE) {
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}
	
	private void refreshButton() {
        final Activity activity = this;
        runOnUiThread(new Runnable() {
            public void run() {
            	Button mButton=(Button)activity.findViewById(R.id.bottom_button);
            	//Log.d("REFR_BUTTON","REFR_BUTTON "+is_rpi_streaming);
            	if (is_rpi_streaming)
            		mButton.setText("Stop");
            	else mButton.setText("Start");
            }
        });
	}
    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized () {
        Log.i ("GStreamer", "Gst initialized. Pipeline status: "+is_playing_desired);
        if (is_playing_desired)
        	nativePlay();
        else nativePause();

        refreshButton();
    }
    
    protected void onSaveInstanceState (Bundle outState) {
        Log.d ("GStreamer", "Saving state, playing:" + is_playing_desired);
        outState.putBoolean("playing", is_playing_desired);
    }
    
    protected void onDestroy() {
        nativeFinalize();
        Log.d("RPI","RPI onDestroy");
        rpi._stop();
        super.onDestroy();
    }
    
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("RPiCameraStreamer");
        nativeClassInit();
    }
    
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        Log.d("GStreamer", "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface created: " + holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface destroyed");
        nativeSurfaceFinalize ();
    }
}
