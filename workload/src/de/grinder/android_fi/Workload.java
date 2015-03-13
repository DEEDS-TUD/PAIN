package de.grinder.android_fi;

import static java.lang.Math.abs;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.util.Log;


public class Workload extends Activity {
	
    private String IO_version = " Android DriveSpeed Benchmark 1.1X ";
    private String IO_title2;
    private String IO_driveUsed;
    private String IO_driveMsg;
    private String IO_drive;
    private Runnable IO__myTask;  
    private String[] IO_xout = new String[20];
    private String IO_x0;
    private String IO_xd$;
    private String IO_id$;
    private String IO_date$;
    private String IO_path;
    private String IO_msg1;
    private static double IO_TimeUsed;
    private static long IO_startTest;
    private static long IO_endTest;
    private static double IO_testTime;  
    private static int IO_pixWide;  
    private static int IO_pixHigh;  
    private static int IO_testDone = 0;  
    private static int IO_size1; 
    private static int IO_sizea = 0; 
    private String[] IO_args = new String[2];
    private int IO_part;
    private int IO_runs;  
    private int IO_driveToUse;
    private int IO_delete = 1;    
    private Context IO_show;
    private boolean IO_mSDcard;
    private boolean IO_hasDirectIO;
    private static final String IO_TAG = "DriveSpeed";
	
	CyclicBarrier barrier;
	
	private static final String TAG = "Workload";

	private static final String FLAGFILE = "workload_state";
	


	//Channels all kinds of exceptions from runWorkload() into this
	class WorkloadException extends Exception {
		WorkloadException() {
			super();
		}

		WorkloadException(String message, Throwable cause) {
			super(message, cause);
		}

		WorkloadException(String message) {
			super(message);
		}

		WorkloadException(Throwable cause) {
			super(cause);
		}
	}

	//Channels all kinds of exceptions from FlagFile handling into this
	class FlagFileException extends Exception {
		FlagFileException() {
			super();
		}

		FlagFileException(String message, Throwable cause) {
			super(message, cause);
		}

		FlagFileException(String message) {
			super(message);
		}

		FlagFileException(Throwable cause) {
			super(cause);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.i(TAG, "Workload live!");

		// separate notifying and workload faults for correct error detection
		try {
			notifyStart();			
			runWorkload();
		} catch (FlagFileException e) {
			Log.e(TAG, "Setting workload-started/finished flag failed: " + e.getMessage(), e);
		}
		catch (WorkloadException e) {
			Log.e(TAG, "Failure spawning workload threads");
		}
	}

	private void runWorkload() throws WorkloadException {
		final boolean notifiedFail[] = new boolean[]{false};
		try {
			
			// barrier for workload synchronization
			 barrier = new CyclicBarrier(2);
			
			
			// spawn thread for I/O load generation 
			new Thread(new Runnable() {
			     public void run() {
			       	
			    	 try {
						IO_x0 = " Not run yet\n\n\n\n\n\n\n\n\n\n\n\n";
						 IO_msg1 = " ****** Normal Write, Read, Delete   ******\n";

						 Date IO_today = Calendar.getInstance().getTime();
						 SimpleDateFormat IO_formatter = new SimpleDateFormat("dd-MMM-yyyy HH.mm");
						 IO_date$ = IO_formatter.format(IO_today);
						 IO_title2 = IO_version + IO_date$ + "\n";
						 IO_part = 0;
						 String IO_AndroidVersion = android.os.Build.VERSION.RELEASE;
						 String IO_state = Environment.getExternalStorageState();

						 if (Environment.MEDIA_MOUNTED.equals(IO_state)) 
						 {
							 IO_mSDcard = true;
						     StatFs IO_stat_fs1 = new StatFs(getApplicationContext().getExternalFilesDir(null).getAbsolutePath());
    
						     double IO_total_sdm_space = (double)IO_stat_fs1.getBlockCount() *(double)IO_stat_fs1.getBlockSize();
						     double IO_free_sdm_space = (double)IO_stat_fs1.getFreeBlocks() *(double)IO_stat_fs1.getBlockSize();
    
						     int IO_MB_Tt1 = (int)(IO_total_sdm_space / 1048576);
						     int IO_MB_Fr1 = (int)(IO_free_sdm_space / 1048576);
    
						     IO_xd$ = " SD Card        MB " +
						            String.format("%7d", IO_MB_Tt1) +
						            " Free " +
						             String.format("%7d", IO_MB_Fr1) +
						             "\n";
						 }
						 else
						 {
							 IO_xd$ = " SD Card Not Available\n";
							 IO_mSDcard = false;
						 }
						 IO_driveToUse = 0;
						 IO_path = getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + "/";
						 Log.i(IO_TAG, "Path: "+IO_path);
						 IO_drive = "        SD Card";
						 IO_clear();
						 IO_xout[15] = " ****** Running Might Take 2 Minutes ******\n";
						 IO_xout[16] = IO_msg1;
						 IO_displayAll(); 
						 IO_startTest = System.currentTimeMillis();
						 IO_TimeUsed = 0.0;
						 
						 Log.i(IO_TAG, "started");
						 int IO_cacheIt = 0;
						 IO_hasDirectIO = true;
						    IO_today = Calendar.getInstance().getTime();
						    IO_formatter = new SimpleDateFormat("dd-MMM-yyyy HH.mm");
						    IO_date$ = IO_formatter.format(IO_today);
						    IO_xout[0]  = IO_title2;
 
						    IO_startTest = System.currentTimeMillis();
						    IO_runs =15;

						    IO_x0 = IO_doIt(-1, IO_path, IO_cacheIt, IO_driveToUse);
						    if (IO_x0.compareTo(" OK") != 0)
						    {
						    	IO_cacheIt = 1;
						    	IO_hasDirectIO = false;
						    	IO_driveMsg = " Data Cached";
						    	IO_driveUsed = "      " + IO_drive + IO_driveMsg + "\n";
						    }
						    else
						    {
						    	IO_driveMsg = " Data Not Cached";
						    	IO_driveUsed = "    " + IO_drive + IO_driveMsg + "\n";            
						    }
						    if (IO_sizea == 88) IO_driveUsed = "      " + IO_drive + " Read Only" + "\n";

						    IO_TimeUsed = 0.0;
						    IO_xout[2] = "\n";  
						    IO_xout[3] = "\n";  
						    IO_xout[5] = "\n";  
						    IO_xout[9] = "\n";  
						    IO_xout[13] = "\n\n";  
						    if (IO_sizea == 88)
						    {                        
						    	IO_x0 = IO_doIt(IO_sizea, IO_path, IO_cacheIt, IO_driveToUse);
						    	IO_xout[2] = IO_x0;
						    	IO_endTest = System.currentTimeMillis();
						    	IO_testTime = (double)(IO_endTest - IO_startTest) / 1000.0;
						    }
						    else
						    {
						        for (IO_size1=0; IO_size1<IO_runs; IO_size1++)
						        {
						           int IO_use;
						           if (IO_size1 == 2 || IO_size1 == 3 || IO_size1 == 5 || IO_size1 == 9 || IO_size1 == 13)
						           {
						        	   IO_use = IO_size1;
						               if (IO_size1 == 2)
						               {
						            	   IO_use = 5;
						               }
						               else if (IO_size1 == 5)
						               {
						            	   IO_use = 2;
						               }
						               IO_x0 = IO_doIt(IO_size1, IO_path, IO_cacheIt, IO_driveToUse);
						               IO_xout[IO_use] = IO_x0;
						               IO_endTest = System.currentTimeMillis();
						               IO_testTime = (double)(IO_endTest - IO_startTest) / 1000.0;
						               if (IO_testTime > 120) IO_size1 = IO_runs;
						           }
						        }
						    }   
						    IO_xout[14] = " No delete\n";
						    if (IO_delete == 1)
						    {
						    	IO_size1 = 99;
						    	IO_x0 = IO_doIt(IO_size1, IO_path, IO_cacheIt, IO_driveToUse);
						    	IO_xout[14] = " Files Deleted\n"; 
						    } 
						    IO_xout[0]  = "                     MBytes/Second\n";
						    IO_xout[1]  = "  MB    Write1 Write2 Write3  Read1  Read2  Read3\n";

 
						    IO_xout[4]  = " Cached\n";
 
						    IO_xout[6] = "\n";
						    IO_xout[7]  = " Random      Write                Read\n";
						    IO_xout[8]  = " From MB     4      8     16      4      8     16\n";

						    IO_xout[10] = "\n";
						    IO_xout[11] = " 200 Files   Write                Read            Delete \n";
						    IO_xout[12] = " File KB     4      8     16      4      8     16   secs \n";
 
   
						    IO_xout[15] = "          Total Elapsed Time  " + String.format("%5.1f", IO_testTime)
						            + " seconds\n";
						    IO_xout[16] = "           Path Used " + IO_path; // driveUsed;
						    IO_testDone = 1;
						    IO_displayAll();
						    Log.i(IO_TAG, "stopped");
					} catch (Exception e) {
						notifiedFail[0] |= true;
					}
			    	try {
			    		barrier.await();
					} catch (InterruptedException ie) {
						Log.e(TAG, "SD card workload thread got interrupted while waiting on barrier");
					} catch (BrokenBarrierException be) {
						Log.e(TAG, "SD card workload thread got broken barrier notification while waiting on barrier");
					}
			    }
			}).start();
			
			
			// Finalize experiment when all workloads are done
			new Thread(new Runnable() {
				boolean resultWritten = false;
			     public void run() {
			    	 try {
			    		 barrier.await();
			    	 } catch (InterruptedException ie) {
			    		 	Log.e(TAG, "post processing thread got interrupted while waiting on barrier");
			    	 } catch (BrokenBarrierException be) {
			        		Log.e(TAG, "post processing thread got broken barrier notification while waiting on barrier");
			    	 }
			    	 while (!resultWritten){
			    		try {
			    			 if (notifiedFail[0]) {
			    				 notifyFailed();
			    				 Log.i(TAG, "Workload failed");
			    			 }
			    			 else notifySuccess();
			    			 resultWritten = true;
			    		 } catch (FlagFileException e) {
								Log.e(TAG, "Setting workload-failed flag failed(" +
										e.getMessage() + "), trying again", e);
			    		 }
			    	 }
			     } 
			}).start();
			
		} catch(Throwable e) {
			throw new WorkloadException(e);
		}
		Log.i(TAG,"ended");
	}

	private void notifyStart() throws FlagFileException {
		writeFlagFile("workload_started");
		Log.d(TAG, "workload_started");
	}

	private void notifySuccess() throws FlagFileException {
		writeFlagFile("workload_finished");
		Log.d(TAG, "workload_finished");
	}

	private void notifyFailed() throws FlagFileException {
		writeFlagFile("workload_failed");
		Log.d(TAG, "workload_failed");
	}

	private void writeFlagFile(String text) throws FlagFileException {
		try {
			removeFlagFile();
			FileOutputStream flagWriter = openFileOutput(FLAGFILE, MODE_PRIVATE);
			if(flagWriter == null) {
				throw new FlagFileException("openFileOutput(FLAGFILE, MODE_PRIVATE) failed");
			}
			flagWriter.write(text.getBytes());
			flagWriter.flush();
		} catch(FlagFileException e) {
			throw e;
		} catch(Throwable e) {
			throw new FlagFileException(e.getClass().getName() + ": " + e.getMessage(), e);
		}
	}

	private void removeFlagFile() throws FlagFileException {
		File flagFile = new File(getFilesDir(), FLAGFILE);
		if (flagFile.exists() && !flagFile.delete()) {
			throw new FlagFileException("Can't remove flag-file \"" + flagFile.getAbsolutePath() + "\"");
		}
	}
	


  ///////////////////////////////////////////////////////////////////
  // nasty copy paste from IO workload app starts
  ///////////////////////////////////////////////////////////////////
  
  public void IO_clear()
  {
	  IO_xout[0]  = IO_title2;
	  IO_xout[1]  = "\n";
	  IO_xout[2]  = " Test 1 - Write and read three 8 and 16 MB files\n";
	  IO_xout[3]  = " Test 2 - Write 8 MB, read can be cached in RAM\n";
	  IO_xout[4]  = " Test 3 - Random write and read 1 KB from 4 to 16 MB\n";
	  IO_xout[5]  = " Test 4 - Write and read 200 files 4 KB to 16 KB\n";
	  IO_xout[6]  = "\n";;
	  IO_xout[7]  = " Use RunS to test SD card, RunI for internal drive,\n";
	  IO_xout[8]  = " Email to save results.\n";
	  IO_xout[9]  = "\n";
	  IO_xout[10] = " Android might force data to be cached, producing\n";
	  IO_xout[11] = " results that represent memory data transfer speed.\n";
	  IO_xout[12] = " In this case, Use More button to choose not to del-\n";   
	  IO_xout[13] = " ete files, switch off and reboot, use More again to\n select read only when RunS or RunI next used.\n";
	  IO_xout[14] = "\n"; 
	  IO_xout[15] = " Can take a while, time out after 120+ seconds\n"; 
	  IO_xout[16] = "\n";
	  IO_testDone = 0;
  }
  
  static 
  {
      System.loadLibrary("drivespeedlib");
  }


  private void IO_displayAll()
  {
          Log.i(IO_TAG, IO_xout[0]  + IO_xout[1]  + IO_xout[2]  + IO_xout[3]  +
        		  IO_xout[4]  + IO_xout[5]  + IO_xout[6]  + IO_xout[7]  + 
                                      IO_xout[8]  + IO_xout[9]  + IO_xout[10] + IO_xout[11] +
                                      IO_xout[12] + IO_xout[13] + IO_xout[14] +
                                                 IO_xout[15] + IO_xout[16]);
  }
   
  public native String IO_doIt(int size1, String path, int cacheIt, int driveToUse);   
  
  ///////////////////////////////////////////////////////////////////
  // nasty copy paste from IO workload app ends
  ///////////////////////////////////////////////////////////////////

  
}
