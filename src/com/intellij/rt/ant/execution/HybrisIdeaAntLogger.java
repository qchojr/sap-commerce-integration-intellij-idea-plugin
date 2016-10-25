/*
 * This file is part of "hybris integration" plugin for Intellij IDEA.
 * Copyright (C) 2014-2016 Alexander Bartash <AlexanderBartash@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.intellij.rt.ant.execution;

import com.intellij.rt.execution.junit.segments.PacketWriter;
import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.listener.AnsiColorLogger;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

/**
 * Created by Martin Zdarsky-Jones on 24/10/16.
 *
 * It's the same class like Intellij's IdeaAntLogger2 class but it fixes
 * https://youtrack.jetbrains.com/issue/IDEA-162949
 * Task started and finished are not logged anymore
 *
 * https://youtrack.jetbrains.com/issue/IDEA-163081
 * exceptions "macro not found" is not logged either
 *
 */
public class HybrisIdeaAntLogger extends DefaultLogger {
    static SegmentedOutputStream ourErr;
    public static final char MESSAGE_CONTENT = 'M';
    public static final char EXCEPTION_CONTENT = 'X';
    public static final char INPUT_REQUEST = 'I';
    public static final char BUILD_END = 'b';
    public static final char BUILD = 'B';
    public static final char TARGET = 'G';
    public static final char TARGET_END = 'g';
    public static final char TASK = 'T';
    public static final char TASK_END = 't';
    public static final char MESSAGE = 'M';
    public static final char ERROR = 'E';
    public static final char EXCEPTION = 'X';
    public static final char EXCEPTION_LINE_SEPARATOR = 0;

    /**
     * @noinspection HardCodedStringLiteral
     */
    public static final String OUTPUT_PREFIX = "IDEA_ANT_INTEGRATION";

    private final HybrisIdeaAntLogger.Priority myMessagePriority = new HybrisIdeaAntLogger.MessagePriority();
    private final HybrisIdeaAntLogger.Priority myTargetPriority = new HybrisIdeaAntLogger.StatePriority(Project.MSG_VERBOSE);
    private final HybrisIdeaAntLogger.Priority myTaskPriority = new HybrisIdeaAntLogger.StatePriority(Project.MSG_VERBOSE);
    private final HybrisIdeaAntLogger.Priority myAlwaysSend = new HybrisIdeaAntLogger.Priority() {
        public void setPriority(int level) {}

        protected boolean shouldSend(int priority) {
            return true;
        }
    };

    public HybrisIdeaAntLogger() {
        guardStreams();
    }

    public synchronized void setMessageOutputLevel(int level) {
        super.setMessageOutputLevel(level);
        myMessagePriority.setPriority(level);
        myTargetPriority.setPriority(level);
        myTaskPriority.setPriority(level);
        myAlwaysSend.setPriority(level);
    }

    public synchronized void buildStarted(BuildEvent event) {
        myAlwaysSend.sendMessage(BUILD, event.getPriority(), "");
    }

    public synchronized void buildFinished(BuildEvent event) {
        myAlwaysSend.sendMessage(BUILD_END, event.getPriority(), event.getException());
    }

    public synchronized void targetStarted(BuildEvent event) {
        myTargetPriority.sendMessage(TARGET, event.getPriority(), event.getTarget().getName());
    }

    public synchronized void targetFinished(BuildEvent event) {
        if (isMarcoNotFound(event)) {
            myTargetPriority.sendMessage(MESSAGE, Project.MSG_INFO, event.getException().getMessage());
            return;
        }
        sendException(event, true);
        myTargetPriority.sendMessage(TARGET_END, event.getPriority(), event.getException());
    }

    public synchronized void taskStarted(BuildEvent event) {
        myTaskPriority.sendMessage(TASK, event.getPriority(), event.getTask().getTaskName());
    }

    public synchronized void taskFinished(BuildEvent event) {
        if (isMarcoNotFound(event)) {
            myTaskPriority.sendMessage(MESSAGE, Project.MSG_INFO, event.getException().getMessage());
            return;
        }
        sendException(event, true);
        myTaskPriority.sendMessage(TASK_END, event.getPriority(), event.getException());
    }

    public synchronized void messageLogged(BuildEvent event) {
        if (isMarcoNotFound(event)) {
            myMessagePriority.sendMessage(MESSAGE, Project.MSG_INFO, event.getException().getMessage());
            return;
        }
        final boolean failOnError = isFailOnError(event);
        if (sendException(event, failOnError)) {
            return;
        }

        int priority = event.getPriority();
        if (priority == Project.MSG_ERR && !failOnError) {
            // some ant tasks (like Copy) with 'failOnError' attribute set to 'false'
            // send warnings with priority level = Project.MSG_ERR
            // this heuristic corrects the priority level, so that IDEA considers the message not as an error but as a warning
            priority = Project.MSG_WARN;
        }

        final String message = event.getMessage();

        if (priority == Project.MSG_ERR) {
            myMessagePriority.sendMessage(ERROR, priority, message);
        }
        else {
            myMessagePriority.sendMessage(MESSAGE, priority, message);
        }
    }

    private boolean isMarcoNotFound(final BuildEvent event) {
        return event.getException() != null && event.getException().getMessage().contains("macro not found");
    }

    private static boolean isFailOnError(BuildEvent event) {
        final Task task = event.getTask();
        if (task != null) {
            try {
                final Field field = task.getClass().getDeclaredField("failonerror");
                field.setAccessible(true);
                return !Boolean.FALSE.equals(field.get(task));
            }
            catch (Exception ignored) {
            }
        }
        return true; // default value
    }

    private boolean sendException(BuildEvent event, boolean isFailOnError) {
        Throwable exception = event.getException();
        if (exception != null) {
            if (isFailOnError) {
                myAlwaysSend.sendMessage(EXCEPTION, event.getPriority(), exception);
                return true;
            }
            myMessagePriority.sendMessage(MESSAGE, Project.MSG_WARN, exception.getMessage());
        }
        return false;
    }

    public static void guardStreams() {
        if (ourErr != null) {
            return;
        }
        PrintStream err = System.err;
        ourErr = new SegmentedOutputStream(err);
        System.setErr(new PrintStream(ourErr));
        ourErr.sendStart();
    }

    private void send(PacketWriter packet) {
        packet.sendThrough(ourErr);
    }

    private PacketWriter createPacket(char id, int priority) {
        PacketWriter packet = PacketFactory.ourInstance.createPacket(id);
        packet.appendLong(priority);
        return packet;
    }

    private abstract class Priority {
        protected void peformSendMessage(char id, int priority, String text) {
            PacketWriter packet = createPacket(id, priority);
            packet.appendChar(MESSAGE_CONTENT);
            packet.appendLimitedString(text);
            send(packet);
        }

        protected void peformSendMessage(char id, int priority, Throwable throwable) {
            if (throwable != null) {
                PacketWriter packet = createPacket(id, priority);
                StringWriter stackTrace = new StringWriter();
                throwable.printStackTrace(new PrintWriter(stackTrace));
                packet.appendChar(EXCEPTION_CONTENT);
                packet.appendLimitedString(stackTrace.toString());
                send(packet);
            } else {
                peformSendMessage(id, priority, "");
            }
        }

        public void sendMessage(char id, int priority, String text) {
            if (shouldSend(priority)) {
                peformSendMessage(id, priority, text);
            }
        }

        public void sendMessage(char id, int priority, Throwable throwable) {
            if (shouldSend(priority)) {
                peformSendMessage(id, priority, throwable);
            }
        }

        public abstract void setPriority(int level);
        protected abstract boolean shouldSend(int priority);
    }

    private class MessagePriority extends HybrisIdeaAntLogger.Priority {
        private int myPriority = Project.MSG_ERR;

        public void setPriority(int level) {
            myPriority = level;
        }

        protected boolean shouldSend(int priority) {
            return priority <= myPriority;
        }
    }

    private class StatePriority extends HybrisIdeaAntLogger.Priority {
        private boolean myEnabled = true;
        private final int myMinLevel;

        public StatePriority(int minLevel) {
            myMinLevel = minLevel;
        }

        public void setPriority(int level) {
            myEnabled = myMinLevel <= level;
        }

        protected boolean shouldSend(int priority) {
            return myEnabled;
        }
    }
}
