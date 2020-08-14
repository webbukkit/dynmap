package org.dynmap.fabric_1_16_1;

import java.util.concurrent.FutureTask;

class TaskRecord implements Comparable<TaskRecord> {
    TaskRecord(long ticktorun, long id, FutureTask<?> future) {
        this.ticktorun = ticktorun;
        this.id = id;
        this.future = future;
    }

    private final long ticktorun;
    private final long id;
    private final FutureTask<?> future;

    void run() {
        this.future.run();
    }

    long getTickToRun() {
        return this.ticktorun;
    }

    @Override
    public int compareTo(TaskRecord o) {
        if (this.ticktorun < o.ticktorun) {
            return -1;
        } else if (this.ticktorun > o.ticktorun) {
            return 1;
        } else if (this.id < o.id) {
            return -1;
        } else if (this.id > o.id) {
            return 1;
        } else {
            return 0;
        }
    }
}
