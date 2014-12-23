/*
 * Copyright (c) 2014 Tim Boudreau
 *
 * This file is part of Scamper.
 *
 * Scamper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.mastfrog.scamper;

import com.google.inject.ImplementedBy;
import io.netty.channel.ChannelHandlerContext;

/**
 * This class is passed errors thrown during message processing.  The
 * default implementation simply prints a stack trace and closes the
 * associated channel.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultErrorHandler.class)
public interface ErrorHandler {
    /**
     * Called when an error occurs
     * @param ctx The channel context
     * @param t The error
     */
    void onError(ChannelHandlerContext ctx, Throwable t);
}
