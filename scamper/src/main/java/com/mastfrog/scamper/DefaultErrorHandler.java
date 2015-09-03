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

import io.netty.channel.ChannelHandlerContext;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of error handler, which simply logs the error and
 * close the context.
 *
 * @author Tim Boudreau
 */
class DefaultErrorHandler implements ErrorHandler {

    private static final Logger logger = Logger.getLogger(DefaultErrorHandler.class.getName());

    @Override
    public void onError(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        logger.log(Level.SEVERE, "Error", cause);
        if (ctx != null) {
            ctx.close();
        }
    }
}
