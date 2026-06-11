package com.huasen.common.service;

import org.springframework.stereotype.Service;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.concurrent.*;

@Service
public class RuntimeCodeExecutor {

    private static final long TIMEOUT_SECONDS = 5;

    public String execute(String code) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            try {
                ScriptEngineManager manager = new ScriptEngineManager();
                ScriptEngine engine = manager.getEngineByName("js");
                if (engine == null) {
                    return "Error: JavaScript engine not available";
                }
                Object result = engine.eval(code);
                return result != null ? result.toString() : "undefined";
            } catch (ScriptException e) {
                return "ScriptError: " + e.getMessage();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        });

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return "Error: execution timeout (exceeded " + TIMEOUT_SECONDS + "s)";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        } finally {
            executor.shutdownNow();
        }
    }
}
