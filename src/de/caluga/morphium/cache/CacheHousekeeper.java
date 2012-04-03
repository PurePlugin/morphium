/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.caluga.morphium.cache;

import de.caluga.morphium.ConfigElement;
import de.caluga.morphium.ConfigManager;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.annotations.caching.Cache;
import de.caluga.morphium.annotations.caching.Cache.ClearStrategy;
import de.caluga.morphium.annotations.caching.NoCache;
import org.apache.log4j.Logger;

import java.util.*;

public class CacheHousekeeper extends Thread {

    private static final String MONGODBLAYER_CACHE = "mongodblayer.cache";
    private int timeout;
    private Map<Class<? extends Object>, Integer> validTimeForClass;
    private int gcTimeout;
    private boolean running = true;
    private Logger log = Logger.getLogger(CacheHousekeeper.class);

    @SuppressWarnings("unchecked")
    public CacheHousekeeper(int houseKeepingTimeout, int globalCacheTimout) {
        this.timeout = houseKeepingTimeout;
        gcTimeout = globalCacheTimout;
        validTimeForClass = new Hashtable<Class<? extends Object>, Integer>();
        setDaemon(true);

        //Last use Configuration manager to read out cache configurations from Mongo!
        Map<String, String> l = ConfigManager.get().getMapSetting(MONGODBLAYER_CACHE);
        if (l != null) {
            for (String k : l.keySet()) {
                String v = l.get(k);
                if (k.endsWith("_max_entries")) {
                    continue;
                }
                if (k.endsWith("_clear_strategy")) {
                    continue;
                }
                try {
                    Class<? extends Object> clz = Class.forName(k);
                    Integer tm = Integer.parseInt(v);
                    validTimeForClass.put(clz, tm);
                } catch (Exception e) {
                    log.error("Error", e);
                }
            }
        } else {
            ConfigElement e = new ConfigElement();
            e.setMapValue(new Hashtable<String, String>());
            e.setName(MONGODBLAYER_CACHE);
//			ConfigManager.get().addSetting("mongodblayer.cache", new Hashtable<String,String>());
            ConfigManager.get().storeSetting(e);
        }
    }

    public void setValidCacheTime(Class<? extends Object> cls, int timeout) {
        validTimeForClass.put(cls, timeout);
    }

    public Integer getValidCacheTime(Class<? extends Object> cls) {
        return validTimeForClass.get(cls);
    }

    public void end() {
        running = false;

    }

    public void run() {
        while (running) {
            try {
                Hashtable<Class, Vector<String>> toDelete = new Hashtable<Class, Vector<String>>();
                Hashtable<Class<? extends Object>, Hashtable<String, CacheElement>> cache = Morphium.get().cloneCache();
                for (Class<? extends Object> clz : cache.keySet()) {
                    Hashtable<String, CacheElement> ch = (Hashtable<String, CacheElement>) cache.get(clz).clone();


                    int maxEntries = -1;
                    Cache cacheSettings = clz.getAnnotation(Cache.class);
                    NoCache noCache = clz.getAnnotation(NoCache.class);
                    int time = gcTimeout;
                    Hashtable<Long, List<String>> lruTime = new Hashtable<Long, List<String>>();
                    Hashtable<Long, List<String>> fifoTime = new Hashtable<Long, List<String>>();
                    ClearStrategy strategy = null;
                    if (noCache == null) {
                        if (cacheSettings != null) {
                            time = cacheSettings.timeout();
                            maxEntries = cacheSettings.maxEntries();
                            strategy = cacheSettings.strategy();

                            if (cacheSettings.overridable()) {
                                ConfigElement setting = ConfigManager.get().getConfigElement(MONGODBLAYER_CACHE);
                                Map<String, String> map = setting.getMapValue();
                                String v = null;
                                if (map != null) {
                                    v = map.get(clz.getName());
                                }
                                if (v == null) {
                                    if (map == null) {
                                        map = new Hashtable<String, String>();
                                        setting.setMapValue(map);
                                    }
                                    setting.getMapValue().put(clz.getName(), "" + time);
                                    setting.getMapValue().put(clz.getName() + "_max_entries", maxEntries + "");
                                    setting.getMapValue().put(clz.getName() + "_clear_strategy", strategy.name());
                                    ConfigManager.get().storeSetting(setting);
                                } else {
                                    try {
                                        time = Integer.parseInt(setting.getMapValue().get(clz.getName()));

                                    } catch (Exception e1) {
                                        Logger.getLogger("MongoDbLayer").warn("Timout could not be parsed for class " + clz.getName());
                                    }
                                    try {
                                        maxEntries = Integer.parseInt(setting.getMapValue().get(clz.getName() + "_max_entries"));
                                    } catch (Exception e1) {
                                        Logger.getLogger("MongoDbLayer").warn("Max Entries could not be parsed for class " + clz.getName() + " Using " + maxEntries);
                                        setting.getMapValue().put(clz.getName() + "_max_entries", "" + maxEntries);
                                        ConfigManager.get().storeSetting(setting);
                                    }
                                    try {
                                        strategy = ClearStrategy.valueOf(setting.getMapValue().get(clz.getName() + "_clear_strategy"));
                                    } catch (Exception e2) {
                                        Logger.getLogger("MongoDbLayer").warn("STrategycould not be parsed for class " + clz.getName() + " Using " + strategy.name());
                                        setting.getMapValue().put(clz.getName() + "_clear_strategy", strategy.name());
                                        ConfigManager.get().storeSetting(setting);
                                    }
                                }
                            }
                            if (validTimeForClass.get(clz) == null) {
                                validTimeForClass.put(clz, time);
                            }
                        }
                    }
                    if (validTimeForClass.get(clz) != null) {
                        time = validTimeForClass.get(clz);
                    }

                    int del = 0;
                    for (String k : ch.keySet()) {
                        CacheElement e = ch.get(k);

                        if (e == null || e.getFound() == null || System.currentTimeMillis() - e.getCreated() > time) {
                            if (toDelete.get(clz) == null) {
                                toDelete.put(clz, new Vector<String>());
                            }
                            toDelete.get(clz).add(k);
                            del++;
                        } else {
                            if (lruTime.get(e.getLru()) == null) {
                                lruTime.put(e.getLru(), new Vector<String>());
                            }
                            lruTime.get(e.getLru()).add(k);
                            long fifo = System.currentTimeMillis() - e.getCreated();
                            if (fifoTime.get(fifo) == null) {
                                fifoTime.put(fifo, new Vector<String>());
                            }
                            fifoTime.get(fifo).add(k);
                        }
                    }
                    cache.put(clz, ch);
                    if (maxEntries > 0 && cache.get(clz).size() - del > maxEntries) {
                        Long[] array = new Long[]{};
                        int idx = 0;
                        switch (strategy) {
                            case LRU:
                                array = lruTime.keySet().toArray(new Long[lruTime.keySet().size()]);
                                Arrays.sort(array);
                                idx = 0;
                                while (cache.get(clz).size() - del > maxEntries) {
                                    if (lruTime.get(array[idx]) != null && lruTime.get(array[idx]).size() != 0) {
                                        if (toDelete.get(clz) == null) {
                                            toDelete.put(clz, new Vector<String>());
                                        }
                                        toDelete.get(clz).add(lruTime.get(array[idx]).get(0));
                                        lruTime.get(array[idx]).remove(0);
                                        del++;
                                        if (lruTime.get(array[idx]).size() == 0) {
                                            idx++;
                                        }

                                    }
                                }
                                break;
                            case FIFO:
                                array = fifoTime.keySet().toArray(new Long[fifoTime.keySet().size()]);
                                Arrays.sort(array);
                                idx = 0;
                                while (cache.get(clz).size() - del > maxEntries) {
                                    if (fifoTime.get(array[array.length - 1 - idx]) != null && fifoTime.get(array[array.length - 1 - idx]).size() != 0) {
                                        if (toDelete.get(clz) == null) {
                                            toDelete.put(clz, new Vector<String>());
                                        }
                                        toDelete.get(clz).add(fifoTime.get(array[array.length - 1 - idx]).get(0));
                                        fifoTime.get(array[array.length - 1 - idx]).remove(0);
                                        del++;
                                        if (fifoTime.get(array[array.length - 1 - idx]).size() == 0) {
                                            idx++;
                                        }
                                    }
                                }
                                break;
                            case RANDOM:
                                array = fifoTime.keySet().toArray(new Long[fifoTime.keySet().size()]);
                                List<Long> lst = Arrays.asList(array);
                                Collections.shuffle(lst);
                                array = lst.toArray(new Long[lst.size()]);
                                idx = 0;
                                while (cache.get(clz).size() - del > maxEntries) {
                                    if (lruTime.get(array[idx]) != null && lruTime.get(array[idx]).size() != 0) {
                                        if (toDelete.get(clz) == null) {
                                            toDelete.put(clz, new Vector<String>());
                                        }
                                        toDelete.get(clz).add(lruTime.get(array[idx]).get(0));
                                        del++;
                                        if (lruTime.get(array[idx]).size() == 0) {
                                            idx++;
                                        }
                                    }
                                }
                                break;
                        }

                    }

                }

                for (Class cls : toDelete.keySet()) {
                    for (String k : toDelete.get(cls)) {
                        cache.get(cls).remove(k);
                    }
                }
                Morphium.get().setCache(cache);


            } catch (Throwable e) {
                log.warn("Error:" + e.getMessage(), e);
            }
            try {
                sleep(timeout);
            } catch (InterruptedException e) {
                log.info("Ignoring InterruptedException");
            }
        }

    }
}