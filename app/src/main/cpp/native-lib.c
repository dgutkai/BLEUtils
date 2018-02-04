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

int adpcm_coder2(short *indata, unsigned char *outdata, int len, struct adpcm_state *state) {
    memcpy(outdata, indata, (size_t)len*2);

}

JNIEXPORT jint Java_com_qcymall_ancdemo_adpcm_AdpcmUtils_adpcmCoder(
        JNIEnv *env,
        jobject obj, jshortArray indata, jcharArray outdata, jint len, jshort valprev, jint index){
    jshort* jInData = (*env)->GetShortArrayElements(env, indata, NULL);
    jbyte * jOutData = (*env)->GetByteArrayElements(env, outdata, NULL);
    struct adpcm_state *state1 = malloc(sizeof(struct adpcm_state));
    state1->index = index;
    state1->valprev = valprev;

    int result = adpcm_coder2(jInData, (unsigned char*)jOutData, len, state1);
    free(state1);
    (*env)->ReleaseByteArrayElements(env, outdata, jOutData, 0);
    return result;
}

JNIEXPORT jint Java_com_qcymall_ancdemo_adpcm_AdpcmUtils_addFromJNI(
        JNIEnv *env,
        jobject obj) {
    return 12;
}
