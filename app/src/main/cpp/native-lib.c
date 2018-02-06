#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
/* Intel ADPCM step variation table */
static int indexTable[16] = {
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8,
};

struct adpcm_state {
    short	valprev;	/* Previous output value */
    char	index;		/* Index into stepsize table */
};

static struct adpcm_state adpcmState;
static int stepsizeTable[89] = {
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
};


int adpcm_coder(short *indata, unsigned char *outdata, int len, struct adpcm_state *state)
{
    int val;			/* Current input sample value */
    unsigned int delta;	/* Current adpcm output value */
    int diff;			/* Difference between val and valprev */
    int step;	        /* Stepsize */
    int valpred;		/* Predicted output value */
    int vpdiff;         /* Current change to valpred */
    int index;			/* Current step change index */
    unsigned int outputbuffer = 0;/* place to keep previous 4-bit value */
    int count = 0;      /* the number of bytes encoded */

    valpred = state->valprev;
    index = (int)state->index;
    step = stepsizeTable[index];

    while (len > 0 ) {
        /* Step 1 - compute difference with previous value */
        val = *indata++;
        diff = val - valpred;
        if(diff < 0)
        {
            delta = 8;
            diff = (-diff);
        }
        else
        {
            delta = 0;
        }

        /* Step 2 - Divide and clamp */
        /* Note:
        ** This code *approximately* computes:
        **    delta = diff*4/step;
        **    vpdiff = (delta+0.5)*step/4;
        ** but in shift step bits are dropped. The net result of this is
        ** that even if you have fast mul/div hardware you cannot put it to
        ** good use since the fixup would be too expensive.
        */
        vpdiff = (step >> 3);

        if ( diff >= step ) {
            delta |= 4;
            diff -= step;
            vpdiff += step;
        }
        step >>= 1;
        if ( diff >= step  ) {
            delta |= 2;
            diff -= step;
            vpdiff += step;
        }
        step >>= 1;
        if ( diff >= step ) {
            delta |= 1;
            vpdiff += step;
        }

        /* Phil Frisbie combined steps 3 and 4 */
        /* Step 3 - Update previous value */
        /* Step 4 - Clamp previous value to 16 bits */
        if ( (delta&8) != 0 )
        {
            valpred -= vpdiff;
            if ( valpred < -32768 )
                valpred = -32768;
        }
        else
        {
            valpred += vpdiff;
            if ( valpred > 32767 )
                valpred = 32767;
        }

        /* Step 5 - Assemble value, update index and step values */
        index += indexTable[delta];
        if ( index < 0 ) index = 0;
        else if ( index > 88 ) index = 88;
        step = stepsizeTable[index];

        /* Step 6 - Output value */
        outputbuffer = (delta << 4);

        /* Step 1 - compute difference with previous value */
        val = *indata++;
        diff = val - valpred;
        if(diff < 0)
        {
            delta = 8;
            diff = (-diff);
        }
        else
        {
            delta = 0;
        }

        /* Step 2 - Divide and clamp */
        /* Note:
        ** This code *approximately* computes:
        **    delta = diff*4/step;
        **    vpdiff = (delta+0.5)*step/4;
        ** but in shift step bits are dropped. The net result of this is
        ** that even if you have fast mul/div hardware you cannot put it to
        ** good use since the fixup would be too expensive.
        */
        vpdiff = (step >> 3);

        if ( diff >= step ) {
            delta |= 4;
            diff -= step;
            vpdiff += step;
        }
        step >>= 1;
        if ( diff >= step  ) {
            delta |= 2;
            diff -= step;
            vpdiff += step;
        }
        step >>= 1;
        if ( diff >= step ) {
            delta |= 1;
            vpdiff += step;
        }

        /* Phil Frisbie combined steps 3 and 4 */
        /* Step 3 - Update previous value */
        /* Step 4 - Clamp previous value to 16 bits */
        if ( (delta&8) != 0 )
        {
            valpred -= vpdiff;
            if ( valpred < -32768 )
                valpred = -32768;
        }
        else
        {
            valpred += vpdiff;
            if ( valpred > 32767 )
                valpred = 32767;
        }

        /* Step 5 - Assemble value, update index and step values */
        index += indexTable[delta];
        if ( index < 0 ) index = 0;
        else if ( index > 88 ) index = 88;
        step = stepsizeTable[index];

        /* Step 6 - Output value */
        *outdata++ = (unsigned char)(delta | outputbuffer);
        count++;
        len -= 2;
    }

    state->valprev = (short)valpred;
    state->index = (char)index;

    return count;
}

int adpcm_decoder(unsigned char *indata, short *outdata, int len, struct adpcm_state *state)
{
    unsigned int delta;	/* Current adpcm output value */
    int step;	        /* Stepsize */
    int valpred;		/* Predicted value */
    int vpdiff;         /* Current change to valpred */
    int index;			/* Current step change index */
    unsigned int inputbuffer = 0;/* place to keep next 4-bit value */
    int count = 0;

    valpred = state->valprev;
    index = (int)state->index;
    step = stepsizeTable[index];

    /* Loop unrolling by Phil Frisbie */
    /* This assumes there are ALWAYS an even number of samples */
    while ( len-- > 0 ) {

        /* Step 1 - get the delta value */
        inputbuffer = (unsigned int)*indata++;
        delta = (inputbuffer >> 4);

        /* Step 2 - Find new index value (for later) */
        index += indexTable[delta];
        if ( index < 0 ) index = 0;
        else if ( index > 88 ) index = 88;


        /* Phil Frisbie combined steps 3, 4, and 5 */
        /* Step 3 - Separate sign and magnitude */
        /* Step 4 - Compute difference and new predicted value */
        /* Step 5 - clamp output value */
        /*
        ** Computes 'vpdiff = (delta+0.5)*step/4', but see comment
        ** in adpcm_coder.
        */
        vpdiff = step >> 3;
        if ( (delta & 4) != 0 ) vpdiff += step;
        if ( (delta & 2) != 0 ) vpdiff += step>>1;
        if ( (delta & 1) != 0 ) vpdiff += step>>2;

        if ( (delta & 8) != 0 )
        {
            valpred -= vpdiff;
            if ( valpred < -32768 )
                valpred = -32768;
        }
        else
        {
            valpred += vpdiff;
            if ( valpred > 32767 )
                valpred = 32767;
        }

        /* Step 6 - Update step value */
        step = stepsizeTable[index];

        /* Step 7 - Output value */
        *outdata++ = (short)valpred;

        /* Step 1 - get the delta value */
        delta = inputbuffer & 0xf;

        /* Step 2 - Find new index value (for later) */
        index += indexTable[delta];
        if ( index < 0 ) index = 0;
        else if ( index > 88 ) index = 88;


        /* Phil Frisbie combined steps 3, 4, and 5 */
        /* Step 3 - Separate sign and magnitude */
        /* Step 4 - Compute difference and new predicted value */
        /* Step 5 - clamp output value */
        /*
        ** Computes 'vpdiff = (delta+0.5)*step/4', but see comment
        ** in adpcm_coder.
        */
        vpdiff = step >> 3;
        if ( (delta & 4) != 0 ) vpdiff += step;
        if ( (delta & 2) != 0 ) vpdiff += step>>1;
        if ( (delta & 1) != 0 ) vpdiff += step>>2;

        if ( (delta & 8) != 0 )
        {
            valpred -= vpdiff;
            if ( valpred < -32768 )
                valpred = -32768;
        }
        else
        {
            valpred += vpdiff;
            if ( valpred > 32767 )
                valpred = 32767;
        }

        /* Step 6 - Update step value */
        step = stepsizeTable[index];

        /* Step 7 - Output value */
        *outdata++ = (short)valpred;
        count += 2;
    }

    state->valprev = (short)valpred;
    state->index = (char)index;

    return count;
}

int adpcm_coder2(short *indata, unsigned char *outdata, int len, struct adpcm_state *state){
    printf("%x, %x, %x", indata[0], indata[1], indata[2]);
    memcpy(outdata, indata, len);
}
int adpcm_decoder2(unsigned char *indata, short *outdata, int len, struct adpcm_state *state){
    printf("%x, %x, %x", outdata[0], outdata[1], outdata[2]);
    memcpy(outdata, indata, len);
}

struct worker
{ int number;
    char name[20];
    int age;
};

int open_file(){
    int ch;//定义文件类型指针
    FILE *fp, *out;;//判断命令行是否正确
    unsigned char buffer[512];
    short resultbuff[1024];
    //按读方式打开由argv[1]指出的文件
    if((fp=fopen("/sdcard/DCS/PCM/ABC1.pcm","rb"))==NULL)
    {
        printf("The file <%s> can not be opened.\n","/sdcard/DCS/PCM/ABC.pcm");//打开操作不成功
        return -1;//结束程序的执行
    }
    if((out=fopen("/sdcard/DCS/PCM/ABC3.pcm","wb"))==NULL)
    {
        printf("The file %s can not be opened.\n","file2.txt");
        return -1;
    }
    while(fread(&buffer,512,1,fp)==1){
        adpcm_decoder(&buffer, &resultbuff, 512, &adpcmState);
        fwrite(&resultbuff,2048,1,out);
    }

    fclose(out);
    fclose(fp); //关闭fp所指文件
}
// 只能在编码开始前调用一次
JNIEXPORT void Java_com_qcymall_ancdemo_adpcm_AdpcmUtils_adpcmReset(
        JNIEnv *env,
        jobject obj){
    adpcmState.index = 0;
    adpcmState.valprev = 0;
    open_file();

}
JNIEXPORT jint Java_com_qcymall_ancdemo_adpcm_AdpcmUtils_adpcmCoder(
        JNIEnv *env,
        jobject obj, jbyteArray indata, jbyteArray outdata, jint len){
    jbyte* jInData_byte = (*env)->GetByteArrayElements(env, indata, NULL);
    short *jInData = (short *)jInData_byte;
    jbyte* jOutData = (*env)->GetByteArrayElements(env, outdata, NULL);
    int result = adpcm_coder(jInData, (unsigned char*)jOutData, len/2, &adpcmState);
    (*env)->ReleaseByteArrayElements(env, outdata, jOutData, 0);
    return result;
}

JNIEXPORT jint Java_com_qcymall_ancdemo_adpcm_AdpcmUtils_adpcmDecoder(
        JNIEnv *env,
        jobject obj, jbyteArray indata, jbyteArray outdata, jint len){
    jbyte* jInData = (*env)->GetByteArrayElements(env, indata, NULL);
    jbyte * jOutData_byte = (*env)->GetByteArrayElements(env, outdata, NULL);
    short *jOutData = (short *)jOutData_byte;
    int result = adpcm_decoder((unsigned char*)jInData, jOutData, len, &adpcmState);
    (*env)->ReleaseByteArrayElements(env, outdata, (jbyte *)jOutData, 0);
    return result;
}
