/*
  ~/workspace/DriveSpeed/jni$ ~/Eclipse/android-ndk-r7/ndk-build
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <time.h> 

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <jni.h>


char resultchars[1000];
char testFile[1000];
char partFile[10];
// char filePath[1000] = "/data/data/com.drivespeed/";


// char filePath[1000] = "/data/data/com/drivespeed/files/";
// char filePath[1000] = "/sdcard/";
// char filePath[1000] = "/LocalDisk/";

double runSecs = 0.1;
double startSecs;
double secs;

int largeFile;
int handle;
int useCache;
int dataOut[262144];
int dataIn[262144];
int dataSize = 1048576;
int smallSize = 1024;

double getTime()
{
  struct timespec tp1;
  clock_gettime(CLOCK_REALTIME, &tp1);
  return (double)tp1.tv_sec + (double)tp1.tv_nsec / 1e9;
}

void start_time()
{
  startSecs = getTime();
  return;
}

void end_time()
{
  secs = getTime() - startSecs;
  return;
}
/*
int writeFile(int use, int dsize)
{
    int  p;

    FILE* file = fopen(testFile, "w+"); 
    if (file == NULL)
    {
        sprintf(resultchars, " Cannot open %s for writing\n", testFile);
        return 0;
    }
    for (p=0; p<use; p++)
    {
       if (fwrite(&dataOut, 1, dsize, file) != dsize )
       {
            sprintf(resultchars," Error writing file %s block %d\n", testFile, p+1);
            return 0;
       }
    }
    fflush(file);
    fclose(file);
    if (largeFile) end_time();
    return 1;
}
*/

int writeFile(int use, int dsize)
{
    int  p;
   
    if (largeFile) start_time();

    if (useCache)
    {
          handle = open(testFile, O_WRONLY | O_CREAT | O_TRUNC,
                                  S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH);
    }
    else  
    {
         handle = open(testFile, O_WRONLY | O_CREAT | O_TRUNC | O_DIRECT | O_SYNC,
                                  S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP| S_IROTH | S_IWOTH); // O_DIRECT | O_SYNC
    }
    if (handle == -1)
    {
        sprintf(resultchars, " Cannot open %s for writing\n", testFile);
        return 0;
    }

    for (p=0; p<use; p++)
    {
       if (write(handle, dataOut, dsize) != dsize )
       {
            sprintf(resultchars," Error writing file %s block %d\n", testFile, p+1);
            return 0;
        }
    }
    close(handle);
    
    if (largeFile) end_time();
    return 1;
}

int readFile(int use, int dsize)
{
    int p;

    if (largeFile) start_time();
    
    handle = open(testFile, O_RDONLY, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH);
    if (handle == -1)
    {
        sprintf(resultchars, " Cannot open %s for reading\n", testFile);
        return 0;
    }

    for (p=0; p<use; p++)
    {
        if (read(handle, dataIn, dsize) == -1)
        {
            sprintf(resultchars," Error reading file %s block %d\n", testFile, p+1);
            return 0;
        }           
 
    }
    close(handle);
    if (largeFile) end_time();
    return 1;
}



JNIEXPORT jstring JNICALL Java_de_grinder_android_1fi_Workload_IO_1doIt(JNIEnv * env, jobject  obj, jint test, jstring path, jint cache, jint dev)
{
    int c, i, f, f1, f2, k, p, s;
    int fileMB;
    int fileKB;
    int files = 3;
    int blockSize;
    int randBlocks;
    int randPos;
    int fileMBrand;
    int totalf;
    int rdonly = 0;
    
    double mbps;
    double msecs;
    double cc;
    double maxdsecs = 0;
    
    char wr[14][14];

    const char * filePath = (*env)->GetStringUTFChars( env, path , NULL ) ;
    sprintf(partFile, "zzztestz%d", dev); 
        
    if (test == -1)
    {
        sprintf(testFile, "%szzztestzz", filePath); 
        handle = open(testFile, O_WRONLY | O_CREAT); 
        if (handle == -1)
        {
           sprintf(resultchars, " Cannot open %s for writing\n", testFile);
           return (*env)->NewStringUTF(env, resultchars);
        }
        else
        {
            close (handle);
            handle = open(testFile, O_WRONLY | O_CREAT | O_DIRECT);
            if (handle == -1)
            {
               sprintf(resultchars, " No O_DIRECT");
            }
            else
            {
               close (handle);
               sprintf(resultchars, " OK");
            }
            return (*env)->NewStringUTF(env, resultchars);
        }
    }
    if (test == 88)
    {
        test = 5;
        rdonly = 1;
    }
    
    if (test == 99)
    {
        for (f=1; f<files+1; f++)
        {
            sprintf(testFile, "%s%s%d", filePath, partFile, f); 
            remove(testFile);
        }

        sprintf(resultchars, " Files deleted \n", testFile);
        return (*env)->NewStringUTF(env, resultchars);
    }
    for (i=0; i<262144; i++)
    {
        dataIn[i]  = 0;
        dataOut[i] = i;
    }
    for (i=0; i<14; i++)
    {
        sprintf(wr[i], " ");
    }
    sprintf(resultchars, " ");

    // Large Files
    
    useCache = 0;
    if (cache == 1) useCache = 1;

    largeFile = 1;
    if (test == 5) fileMB =  4;
    if (test == 3) fileMB = 8;

    if (test == 2)
    {
        fileMB = 4;
        useCache = 1;
    }
     
    c = 0;
    if (test < 6)
    {
        // Write
       
        for (f=1; f<files+1; f++)
        {
            if (rdonly == 0)
            {
                sprintf(testFile, "%s%s%d", filePath, partFile, f); 
                if (!writeFile(fileMB, dataSize))
                {
                    for (f2=0; f2<f; f2++)
                    {
                       sprintf(testFile, "%s%s%d", filePath, partFile, f2+1);       
                       remove(testFile);
                    }
                    return (*env)->NewStringUTF(env, resultchars);
                }
                mbps = (double)fileMB * (double)dataSize/ 1000000.0 / secs;
                sprintf(wr[c], "%7.1f", mbps);
            }
            else
            {
                mbps = 0;
                sprintf(wr[c], "%7.1f", mbps);
            }
            c = c + 1;
        }

        //  Read

        for (f=1; f<files+1; f++)
        {
            sprintf(testFile, "%s%s%d", filePath, partFile, f); 
            if (!readFile(fileMB, dataSize))  
            {
                for (f=1; f<files+1; f++)
                {
                  sprintf(testFile, "%s%s%d", filePath, partFile, f); 
                  remove(testFile);
                }  
                return (*env)->NewStringUTF(env, resultchars);
            }
            mbps = (double)fileMB * (double)dataSize/ 1000000.0 / secs;
            sprintf(wr[c], "%7.1f", mbps);
            c = c + 1;
        }
        if (test < 5)
        {
            for (f=1; f<files+1; f++)
            {
                sprintf(testFile, "%s%s%d", filePath, partFile, f); 
                remove(testFile);
            }
        }
        sprintf(resultchars, "%4d   %s%s%s%s%s%s\n", fileMB, wr[0], wr[1], wr[2], wr[3], wr[4], wr[5]);
    }
    else if (test == 9)
    {
        // Random
        
        fileMBrand = 8;
        randBlocks = fileMBrand * 1024;
        blockSize = 1024;
        srand(123);

        // Write
    
        sprintf(testFile, "%s%srand", filePath, partFile); 
        if (!writeFile(fileMBrand, dataSize))
        {
            remove(testFile);
            return (*env)->NewStringUTF(env, resultchars);
        }
        close (handle);
   
       // Random Read
        
        c = 3;
        for (p=4; p<fileMBrand+1; p=p*2)
        {
            handle = open(testFile, O_RDONLY | O_SYNC, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH);
            if (handle == -1)
            {
                sprintf(resultchars, " Cannot open %s for reading\n", testFile);
                remove(testFile);
                return (*env)->NewStringUTF(env, resultchars);
            }

            cc = 0.0;
            start_time();
            do
            {
                for (k=0; k<25; k++)
                {
                    randPos = (int)((double)p * 1024.0 * ((double)(rand()) / (double)2147483648.0));
                    if (randPos == randBlocks) randPos = randPos - 1;
                    lseek(handle, randPos * 1024, SEEK_SET);
                    if (read(handle, dataIn, blockSize) == -1)
                    {
                        sprintf(resultchars, " Error reading file randomly\n");
                        remove(testFile);
                        return (*env)->NewStringUTF(env, resultchars);
                    }
                }
                end_time();
                cc = cc + 25.0;
            }
            while (secs < 1.0);
            msecs = 1000.0 * secs / cc;
            sprintf(wr[c], "%7.2f", msecs);
            c = c + 1;            
            close (handle);
        }
        
       // Random Write
        
        c = 0;
        for (p=4; p<fileMBrand+1; p=p*2)
        {
            handle = open(testFile, O_WRONLY | O_SYNC,
                                      S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH);
            if (handle == -1)
            {
                sprintf(resultchars, " Cannot open %s for writing\n", testFile);
                remove(testFile);
                return (*env)->NewStringUTF(env, resultchars);
            }
        
            cc = 0.0;
            start_time();
            do
            {
                for (k=0; k<25; k++)
                {
                    randPos = (int)((double)p * 1024 * ((double)(rand()) / (double)2147483648.0));
                    if (randPos == randBlocks) randPos = randPos - 1;
                    lseek(handle, randPos * 1024, SEEK_SET);
                    if (write(handle, dataOut, blockSize) == -1)
                    {
                        sprintf(resultchars, " Error writing file randomly\n");
                        remove(testFile);
                        return (*env)->NewStringUTF(env, resultchars);
                    }
                }
                end_time();
                cc = cc + 25.0;
            }
            while (secs < 1.0);
            msecs = 1000.0 * secs / cc;
            sprintf(wr[c], "%7.2f", msecs);
            c = c + 1;            
            close (handle);
         }        
         remove(testFile);
         sprintf(resultchars,  " msecs %s%s%s%s%s%s\n", wr[0], wr[1], wr[2], wr[3], wr[4], wr[5]); 
    }
    else if (test == 13)
    {
        // Small Files
    
        largeFile = 0;
        useCache = 0;
        if (cache == 1) useCache = 1;
        f1 = 1000;
        f2 = 1200;
        totalf = f2 - f1;
        c = 0;       
   
        for (fileKB=4; fileKB<17; fileKB=fileKB*2)
        {
            if (fileKB > 4) sleep(5);
            
            // Write

            start_time();
            for (f=f1; f<f2; f++)
            {
                sprintf(testFile, "%s%s%d", filePath, partFile, f); 
                if (!writeFile(1, smallSize * fileKB))
                {
                    for (f=f1; f<f2; f++)
                    {
                        sprintf(testFile, "%s%s%d", filePath, partFile, f);
                        remove(testFile);
                    }
                    return (*env)->NewStringUTF(env, resultchars);
                }

            }
            end_time();
            mbps = (double)totalf * (double)fileKB * (double)smallSize / 1000000.0 / secs;
            msecs = secs * 1000.0 / (double)totalf;
            sprintf(wr[c], "%7.2f", mbps);
            sprintf(wr[c + 6], "%7.2f", msecs);

            // Read
            
            start_time();
            for (f=f1; f<f2; f++)
            { 
                sprintf(testFile, "%s%s%d", filePath, partFile, f);
                if (!readFile(1, smallSize * fileKB))
                {
                    for (f=f1; f<f2; f++)
                    {
                        sprintf(testFile, "%s%s%d", filePath, partFile, f); 
                        remove(testFile);
                    }
                    return (*env)->NewStringUTF(env, resultchars);
                }
            }
            end_time();
            mbps = (double)totalf * (double)fileKB * (double)smallSize / 1000000.0 / secs;
            msecs = secs * 1000.0 / (double)totalf;
            sprintf(wr[c + 3], "%7.2f", mbps);
            sprintf(wr[c + 9], "%7.2f", msecs);
            
            start_time();
            for (f=f1; f<f2; f++)
            {
                sprintf(testFile, "%s%s%d", filePath, partFile, f); 
                remove(testFile);
            }
            end_time();
            if (secs > maxdsecs) maxdsecs = secs;
            c = c + 1;
        }
        sprintf(wr[12], "  ");
        sprintf(wr[13], "%7.3f", maxdsecs);


        sprintf(resultchars,  " MB/sec%s%s%s%s%s%s%s\n msecs %s%s%s%s%s%s%s\n",
                                     wr[0], wr[1], wr[2], wr[3], wr[ 4], wr[ 5], wr[12],
                                     wr[6], wr[7], wr[8], wr[9], wr[10], wr[11], wr[13]);

    }    
    return (*env)->NewStringUTF(env, resultchars);
}

      
