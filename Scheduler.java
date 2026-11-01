package com.mattercontrol.matter;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny server-side "run this N ticks from now" helper used by multi-stage powers. */
public final class Scheduler {
    private Scheduler() {}

    private static final class Task {
        int delay;
        final Runnable action;

        Task(int delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    public static void later(int ticks, Runnable action) {
        TASKS.add(new Task(Math.max(1, ticks), action));
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TASKS.isEmpty()) return;
            List<Task> due = new ArrayList<>();
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (--t.delay <= 0) {
                    due.add(t);
                    it.remove();
                }
            }
            for (Task t : due) {
                try {
                    t.action.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TASKS.clear());
    }
}
