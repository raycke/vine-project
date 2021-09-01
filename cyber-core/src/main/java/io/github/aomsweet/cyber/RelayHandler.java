/*
  Copyright 2021 The Cyber Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package io.github.aomsweet.cyber;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author aomsweet
 */
public abstract class RelayHandler extends ChannelInboundHandlerAdapter {

    protected final InternalLogger logger;
    protected final CyberServer cyber;

    protected State state;
    protected Channel relayChannel;

    public RelayHandler(CyberServer cyber, InternalLogger logger) {
        this.cyber = cyber;
        this.logger = logger;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("{} INACTIVE. CLOSING RELAY CHANNEL {}", ctx.channel(), relayChannel);
        }
        release(ctx);
    }

    public void relay(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(msg);
        } else {
            release(ctx);
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean isWritable = ctx.channel().isWritable();
        if (logger.isDebugEnabled()) {
            logger.debug("{} WRITABILITY CHANGED. CURRENT STATUS: {}. {} {}", ctx.channel(),
                isWritable ? "WRITABLE" : "NOT WRITABLE", relayChannel,
                isWritable ? "ENABLE AUTO READ" : "DISABLE AUTO READ");
        }
        relayChannel.config().setAutoRead(isWritable);
    }

    public final void release(ChannelHandlerContext ctx) {
        if (state != State.RELEASED) {
            destroy(ctx);
            state = State.RELEASED;
        }
    }

    protected void destroy(ChannelHandlerContext ctx) {
        if (logger.isDebugEnabled()) {
            logger.debug("Channel released. {}", ctx.channel());
        }
        ctx.close();
        if (relayChannel != null && relayChannel.isActive()) {
            relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            if (cause instanceof IOException || cause instanceof RejectedExecutionException) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{}: {}", cause.getClass().getName(), cause.getMessage(), cause);
                } else {
                    logger.info("{}: {}", cause.getClass().getName(), cause.getMessage());
                }
            } else {
                logger.error("{}: {}", cause.getClass().getName(), cause.getMessage(), cause);
            }
        } finally {
            release(ctx);
        }
    }

    public enum State {

        UNCONNECTED, CONNECTED, READY, RELEASED

    }

}
