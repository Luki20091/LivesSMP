package me.sparklee.LivesSMP.tasks;

import me.sparklee.LivesSMP.LivesSMP;
import me.sparklee.LivesSMP.utils.DebugLog;

public class DebugMemorySamplerTask implements Runnable {

    private final LivesSMP plugin;

    public DebugMemorySamplerTask(LivesSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        DebugLog.memory(plugin, "sampler");
    }
}
