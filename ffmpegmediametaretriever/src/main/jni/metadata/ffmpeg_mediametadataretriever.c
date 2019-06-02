//
// Created by Administrator on 2019/6/1.
//

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libavutil/opt.h>
#include <ffmpeg_mediametadataretriever.h>
#include <ffmpeg_utils.h>

#include <stdio.h>
#include <unistd.h>

#include <android/log.h>

const int TARGET_IMAGE_FORMAT=AV_PIX_FMT_RGBA;
const int TARGET_IMAGE_CODEC=AV_CODEC_ID_PNG;

void convert_image(State *state, AVCodecContext *pCodecCtx, AVFrame *pFrame, AVPacket *avpkt, int *got_packet_ptr, int width, int height);

int is_supported_format(int codec_id, int pix_fmt)
{
    if((codec_id==AV_CODEC_ID_PNG || codec_id==AV_CODEC_ID_MJPEG ||
        codec_id==AV_CODEC_ID_BMP )&&(pix_fmt==AV_PIX_FMT_RGBA))
    {
        return 1;
    }

    return 0;
}

int get_scaled_context(State *s, AVCodecContext *pCodecContext, int width, int height)
{
    AVCodec *targetCodec=avcodec_find_encoder(TARGET_IMAGE_CODEC);
    if(!targetCodec)
    {
        printf("avcodec_find_decoder() failed to find encoder\n");
        return FAILURE;
    }

    s->scaled_codecCtx=avcodec_alloc_context3(targetCodec);
    if(!s->scaled_codecCtx)
    {
        printf("avcodec_alloc_context3 failed\n");
        return FAILURE;
    }

    s->scaled_codecCtx->bit_rate=s->video_st->codec->bit_rate;
    s->scaled_codecCtx->width=width;
    s->scaled_codecCtx->height=height;
    s->scaled_codecCtx->pix_fmt=TARGET_IMAGE_FORMAT;
    s->scaled_codecCtx->codec_type=AVMEDIA_TYPE_VIDEO;
    s->scaled_codecCtx->time_base.num=s->video_st->codec->time_base.num;
    s->scaled_codecCtx->time_base.den=s->video_st->codec->time_base.den;

    if(!targetCodec||avcodec_open2(s->scaled_codecCtx, targetCodec, NULL)<0)
    {
        printf("avcodec_open2() failed\n");
        return FAILURE;
    }

    s->scaled_sws_ctx=sws_getContext(s->video_st->codec->width, s->video_st->codec->height,
            s->video_st->codec->pix_fmt, width, height, TARGET_IMAGE_FORMAT, SWS_BILINEAR, NULL,
            NULL, NULL);

    return SUCCESS;
}

int stream_component_open(State *s, int stream_index)
{
    AVFormatContext *pFormatCtx=s->pFormatCtx;
    AVCodecContext *codecCtx;
    AVCodec *codec;

    if(stream_index<0||stream_index>=pFormatCtx->nb_streams)
    {
        return FAILURE;
    }

    codecCtx=pFormatCtx->streams[stream_index]->codec;

    const AVCodecDescriptor *codesc=avcodec_descriptor_get(codecCtx->codec_id);
    if(codesc)
    {
        printf("avcodec_find_decoder %s\n", codesc->name);
    }

    codec=avcodec_find_decoder(codecCtx->codec_id);

    if(codec==NULL)
    {
        printf("avcodec_find_decoder() failed to find audio decoder\n");
        return FAILURE;
    }

    if(!codec||(avcodec_open2(codecCtx, codec, NULL)<0))
    {
        printf("avcodec_open2() failed\n");
        return FAILURE;
    }

    switch(codecCtx->codec_type)
    {
        case AVMEDIA_TYPE_AUDIO:
            s->audio_stream=stream_index;
            s->audio_st=pFormatCtx->streams[stream_index];
            break;
        case AVMEDIA_TYPE_VIDEO:
            s->video_stream=stream_index;
            s->video_st=pFormatCtx->streams[stream_index];

            AVCodec *targetCodec=avcodec_find_encoder(TARGET_IMAGE_CODEC);
            if(!targetCodec)
            {
                printf("avcodec_find_decoder() failed to find encoder\n");
                return FAILURE;
            }

            s->codecCtx->bit_rate=s->video_st->codec->bit_rate;
            s->codecCtx->width=s->video_st->codec->width;
            s->codecCtx->height=s->video_st->codec->height;
            s->codecCtx->pix_fmt=TARGET_IMAGE_FORMAT;
            s->codecCtx->codec_type=AVMEDIA_TYPE_VIDEO;
            s->codecCtx->time_base.num=s->video_st->codec->time_base.num;
            s->codecCtx->time_base.den=s->video_st->codec->time_base.den;

            if(!targetCodec||avcodec_open2(s->codecCtx,targetCodec, NULL)<0)
            {
                printf("avcodec_open2() failed\n");
                return FAILURE;
            }

            s->sws_ctx=sws_getContext(s->video_st->codec->width, s->video_st->codec->height,
                    s->video_st->codec->pix_fmt, s->video_st->codec->width, s->video_st->codec->height,
                    TARGET_IMAGE_FORMAT, SWS_BILINEAR, NULL, NULL, NULL);
            break;
        default:
            break;
    }

    return SUCCESS;
}