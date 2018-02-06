/*
   ~/workspace/RandMem/jni$ ~/Eclipse/android-ndk-r7/ndk-build

 * RandMem Benchmark  Copyright (c) Roy Longbottom
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <time.h>
#include <jni.h>


int    testII (int jloop, int arraymax);
int    testIIR(int jloop, int arraymax);
int    testJI (int jloop, int arraymax);
int    testJIR(int jloop, int arraymax);


int checkTime(int jm);

// ************* GLOBAL VARIABLES **********

int    xi[16779000]; // 64 MB + 0.7 MB;

int  arraymax;
int   randNumbers;
int   randMax;
char  startRam[200];

double   runSecs = 0.1; // 100 ms;
double   startSecs;
double   secs;
char     resultchars[1000];

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


int testJIR(int jloop, int arraymax)
{
    int i, j;
    int toti;

    for (j=0; j<jloop; j++)
    {
        toti = 0;
        for (i=0; i<arraymax; i=i+32)
        {
            xi[xi[i+28]] = xi[xi[i+30]];
            xi[xi[i+24]] = xi[xi[i+26]];
            xi[xi[i+20]] = xi[xi[i+22]];
            xi[xi[i+16]] = xi[xi[i+18]];
            xi[xi[i+12]] = xi[xi[i+14]];
            xi[xi[i+ 8]] = xi[xi[i+10]];
            xi[xi[i+ 4]] = xi[xi[i+ 6]];
            xi[xi[i+ 0]] = xi[xi[i+ 2]];
        }
    }
    return toti;
}

int testJI(int jloop, int arraymax)
{
    int i, j;
    int toti;

    for (j=0; j<jloop; j++)
    {
        toti = 0;
        for (i=0; i<arraymax; i=i+32)
        {
            xi[xi[i+ 2]] = xi[xi[i+ 0]];
            xi[xi[i+ 6]] = xi[xi[i+ 4]];
            xi[xi[i+10]] = xi[xi[i+ 8]];
            xi[xi[i+14]] = xi[xi[i+12]];
            xi[xi[i+18]] = xi[xi[i+16]];
            xi[xi[i+22]] = xi[xi[i+20]];
            xi[xi[i+26]] = xi[xi[i+24]];
            xi[xi[i+30]] = xi[xi[i+28]];
        }
    }
    return toti;
}

int testIIR(int jloop, int arraymax)
{
    int i, j;
    int toti;

    for (j=0; j<jloop; j++)
    {
        toti = 0;
        for (i=0; i<arraymax; i=i+32)
        {
            toti = toti & xi[xi[i+30]] | xi[xi[i+28]]
                   & xi[xi[i+26]] | xi[xi[i+24]]
                   & xi[xi[i+22]] | xi[xi[i+20]]
                   & xi[xi[i+18]] | xi[xi[i+16]]
                   & xi[xi[i+14]] | xi[xi[i+12]]
                   & xi[xi[i+10]] | xi[xi[i+ 8]]
                   & xi[xi[i+ 6]] | xi[xi[i+ 4]]
                   & xi[xi[i+ 2]] | xi[xi[i+ 0]];
        }
    }
    return toti;
}

int testII(int jloop, int arraymax)
{
    int i, j;
    int toti;

    for (j=0; j<jloop; j++)
    {
        toti = 0;
        for (i=0; i<arraymax; i=i+32)
        {
            toti = toti & xi[xi[i+ 0]] | xi[xi[i+ 2]]
                   & xi[xi[i+ 4]] | xi[xi[i+ 6]]
                   & xi[xi[i+ 8]] | xi[xi[i+10]]
                   & xi[xi[i+12]] | xi[xi[i+14]]
                   & xi[xi[i+16]] | xi[xi[i+18]]
                   & xi[xi[i+20]] | xi[xi[i+22]]
                   & xi[xi[i+24]] | xi[xi[i+26]]
                   & xi[xi[i+28]] | xi[xi[i+30]];
        }
    }
    return toti;
}


jstring
Java_de_grinder_android_1fi_Workload_MEM_1stringFromJNI( JNIEnv* env, jobject thiz, jint pass)
{
    int i, g, k;
    int jmax;
    int toti;
    int toty = 0;
    double sbytes = 4.0;
    double mega = 1024.0 * 1024.0;
    double res0 = 0;
    double res1 = 0;
    double res2 = 0;
    double res3 = 0;
    unsigned int    memoryKBytes[20];

    memoryKBytes[0] = 16;
    memoryKBytes[1] = 32;
    memoryKBytes[2] = 64;
    memoryKBytes[3] = 128;
    memoryKBytes[4] = 256;
    memoryKBytes[5] = 512;
    memoryKBytes[6] = 1024;
    memoryKBytes[7] = 4096;
    memoryKBytes[8] = 16384;
    memoryKBytes[9] = 65536;

    arraymax = (int)((double)memoryKBytes[pass] * 1024.0 / sbytes);

    // Serial Read Integer *********************

    for (i=0; i<arraymax+1; i++)
    {
        xi[i] = i+1;
    }
    jmax = 1;
    do
    {
        start_time();
        toti = testII(jmax, arraymax);
        end_time();
        jmax = checkTime(jmax);
    }
    while (secs < runSecs);
    res0 =  (double)arraymax * (double)jmax * sbytes / mega / secs;
    toty = toty + toti;

    // Serial Read/Write Integer *********************

    for (i=0; i<arraymax+1; i++)
    {
        xi[i] = i+1;
    }

    jmax = 1;
    sbytes = 4.0;
    do
    {
        start_time();
        toti = testJI(jmax, arraymax);
        end_time();
        jmax = checkTime(jmax);
    }
    while (secs < runSecs);
    res1 =  (double)arraymax * (double)jmax * sbytes / mega / secs;
    toty = toty - toti;

    // Random Read Integer *********************

    srand(123);

    for (i=0; i<arraymax+1; i++)
    {
        xi[i] = (int)((double)arraymax * ((double)(rand()) / (double)2147483648.0));
    }


    jmax = 1;
    sbytes = 4.0;
    do
    {
        start_time();
        toti = testIIR(jmax, arraymax);
        end_time();
        jmax = checkTime(jmax);
    }
    while (secs < runSecs);
    res2 =  (double)arraymax * (double)jmax * sbytes / mega / secs;
    toty = toty + toti;


    // Random Read/Write Integer *********************

    srand(123);
    for (i=0; i<arraymax+1; i++)
    {
        xi[i] = (int)((double)arraymax * ((double)(rand()) / (double)2147483648.0));
    }

    jmax = 1;
    sbytes = 4.0;
    do
    {
        start_time();
        toti = testJIR(jmax, arraymax);
        end_time();
        jmax = checkTime(jmax);
    }
    while (secs < runSecs);
    res3 =  (double)arraymax * (double)jmax * sbytes / mega / secs;
    toty = toty - toti;
    if (toty == 12345)
    {
        sprintf(resultchars, " Gone Wrong\n");
    }
    else
    {
        sprintf(resultchars, " %8d %8.0f %8.0f %8.0f %8.0f\n",
                memoryKBytes[pass], res0, res1, res2, res3);
    }
    return (*env)->NewStringUTF(env, resultchars);

} // runTests



int checkTime(int jm)
{
    if (secs < runSecs)
    {
        if (secs < runSecs / 8.0)
        {
            jm = jm * 10;
        }
        else
        {
            jm = (int)(runSecs * 1.25 / secs * (double)jm+1);
        }
    }
    return jm;
}


