package de.caluga.test.mongo.suite;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumSingleton;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.caching.NoCache;
import de.caluga.morphium.annotations.caching.WriteBuffer;
import de.caluga.morphium.query.Query;
import de.caluga.morphium.writer.BufferedMorphiumWriterImpl;
import org.junit.Test;

/**
 * User: Stephan Bösebeck
 * Date: 22.03.13
 * Time: 14:41
 * <p/>
 * TODO: Add documentation here
 */
public class BufferedWriterTest extends MongoTest {

    @Test
    public void testWriteBufferUpsert() throws Exception {
        MorphiumSingleton.get().dropCollection(BufferedBySizeObject.class);

        waitForAsyncOperationToStart(10000);
        waitForWrites();

        Query<BufferedBySizeObject> q = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class).f("counter").eq(100);
        MorphiumSingleton.get().inc(q, "counter", 1, true, false);

        q = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class).f("counter").eq(101);
        MorphiumSingleton.get().inc(q, "counter", 1.0, true, false);

        q = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class).f("counter").eq(201);
        MorphiumSingleton.get().dec(q, "counter", 1.0, true, false);

        q = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class).f("counter").eq(300);
        MorphiumSingleton.get().set(q, "counter", 1, true, false);

        waitForAsyncOperationToStart(10000);
        waitForWrites();
        Thread.sleep(3000);
        q = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class);
        assert (q.countAll() == 3);
        for (BufferedBySizeObject o : q.asList()) {
            log.info("Counter: " + o.getCounter());
        }
    }


    @Test
    public void testWriteBufferIncs() throws Exception {
        MorphiumSingleton.get().dropCollection(BufferedBySizeObject.class);

        waitForAsyncOperationToStart(10000);
        waitForWrites();
        BufferedMorphiumWriterImpl wr = (BufferedMorphiumWriterImpl) morphium.getWriterForClass(BufferedBySizeObject.class);

        Query<BufferedBySizeObject> q = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class).f("counter").eq(100);
        MorphiumSingleton.get().inc(q, "dval", 1, true, false);
//        assert(wr.writeBufferCount()>1);

        q = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class).f("counter").eq(100);
        MorphiumSingleton.get().inc(q, "dval", 1.0, true, false);
//        assert(wr.writeBufferCount()>1);

        q = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class).f("counter").eq(100);
        MorphiumSingleton.get().dec(q, "dval", 1.0, true, false);
//        assert(wr.writeBufferCount()>1);



        waitForAsyncOperationToStart(10000);
        waitForWrites();
        Thread.sleep(3000);
        q = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class);
        assert (q.countAll() == 1) : "Counted " + q.countAll();
        BufferedBySizeObject o = q.get();
        log.info("Counter: " + o.getCounter());
        assert (o.getCounter() == 100);
        assert (o.getDval() == 1.0);
    }

    @Test
    public void testWriteBufferBySizeWithWriteNewStrategy() throws Exception {
        MorphiumSingleton.get().dropCollection(BufferedBySizeWriteNewObject.class);
        waitForAsyncOperationToStart(10000);
        waitForWrites();
        int amount = 1500;
        for (int i = 0; i < amount; i++) {
            BufferedBySizeWriteNewObject bo = new BufferedBySizeWriteNewObject();
            bo.setCounter(i);
            bo.setValue("a value");
            MorphiumSingleton.get().store(bo);
        }
        long start = System.currentTimeMillis();
        long l = System.currentTimeMillis() - start;
        while (true) {

            long count = MorphiumSingleton.get().createQueryFor(BufferedBySizeWriteNewObject.class).countAll();
            if (count == amount) break;
            log.info("Amount written: " + count + " but Write buffer: " + MorphiumSingleton.get().getWriteBufferCount());
            Thread.sleep(1000);
        }
        log.info("All written " + amount);
    }

    @Test
    public void testWriteBufferBySize() throws Exception {
        MorphiumSingleton.get().dropCollection(BufferedBySizeObject.class);
        int amount = 1500;
        for (int i = 0; i < amount; i++) {
            BufferedBySizeObject bo = new BufferedBySizeObject();
            bo.setCounter(i);
            bo.setValue("a value");
            MorphiumSingleton.get().store(bo);
        }
        long start = System.currentTimeMillis();
        while (true) {

            long count = MorphiumSingleton.get().createQueryFor(BufferedBySizeObject.class).countAll();
            int writeBufferCount = MorphiumSingleton.get().getWriteBufferCount();
            System.out.println("Amount written: " + count + " but Write buffer: " + writeBufferCount);
            Thread.sleep(10);
            if (writeBufferCount == 0) break;
        }
        assert (System.currentTimeMillis() - start < 120000);
    }

    @Test
    public void testWriteBufferByTime() throws Exception {
        MorphiumSingleton.get().dropCollection(BufferedByTimeObject.class);
        int amount = 1500;
        for (int i = 0; i < amount; i++) {
            BufferedByTimeObject bo = new BufferedByTimeObject();
            bo.setCounter(i);
            bo.setValue("a value");
            MorphiumSingleton.get().store(bo);
        }
        long start = System.currentTimeMillis();
        while (true) {

            long count = MorphiumSingleton.get().createQueryFor(BufferedByTimeObject.class).countAll();
            if (count == amount) break;
            System.out.println("Amount written: " + count + " but Write buffer: " + MorphiumSingleton.get().getWriteBufferCount());
//            assert (MorphiumSingleton.get().getWriteBufferCount() != 0);
            Thread.sleep(100);
        }
        assert (System.currentTimeMillis() - start < 120000);
    }

    @Test
    public void testWriteBufferBySizeWithDelOldStrategy() throws Exception {
        MorphiumSingleton.get().dropCollection(BufferedBySizeDelOldObject.class);
        waitForAsyncOperationToStart(10000);
        waitForWrites();
        int amount = 1500;
        for (int i = 0; i < amount; i++) {
            BufferedBySizeDelOldObject bo = new BufferedBySizeDelOldObject();
            bo.setCounter(i);
            bo.setValue("a value");
            MorphiumSingleton.get().store(bo);
        }
        log.info("Writes prepared - waiting");
        Thread.sleep(4000);
        long count = MorphiumSingleton.get().createQueryFor(BufferedBySizeDelOldObject.class).countAll();
        log.info("Amount written: " + count);
    }

    @Test
    public void testWriteBufferBySizeWithIngoreNewStrategy() throws Exception {
        MorphiumSingleton.get().dropCollection(BufferedBySizeIgnoreNewObject.class);
        waitForAsyncOperationToStart(10000);
        waitForWrites();
        int amount = 1500;
        for (int i = 0; i < amount; i++) {
            BufferedBySizeIgnoreNewObject bo = new BufferedBySizeIgnoreNewObject();
            bo.setCounter(i);
            bo.setValue("a value");
            MorphiumSingleton.get().store(bo);
        }
        log.info("Writes prepared - waiting");
        Thread.sleep(4000);
        long count = MorphiumSingleton.get().createQueryFor(BufferedBySizeIgnoreNewObject.class).countAll();
        assert (count < 1500);
    }

    @Test
    public void testComplexObject() throws Exception {
        Morphium m = MorphiumSingleton.get();
        m.dropCollection(ComplexObjectBuffered.class);
        for (int i = 0; i < 100; i++) {
            ComplexObjectBuffered buf = new ComplexObjectBuffered();
            buf.setEinText("The text " + i);
            EmbeddedObject e = new EmbeddedObject();
            e.setName("just a test");
            buf.setEmbed(e);
            UncachedObject uc = new UncachedObject();
            uc.setCounter(i);
            uc.setValue("value");
            buf.setEntityEmbeded(uc);
            m.store(buf);
        }
        while (m.getWriteBufferCount() != 0) Thread.sleep(100);

        ComplexObjectBuffered buf = m.createQueryFor(ComplexObjectBuffered.class).f("ein_text").eq("The text " + 0).get();
        assert (buf != null);
        assert (m.createQueryFor(ComplexObjectBuffered.class).countAll() == 100);
    }

    @Test
    public void testNonObjectIdID() throws Exception {
        Morphium m = MorphiumSingleton.get();
        m.dropCollection(SimpleObject.class);
        for (int i = 0; i < 100; i++) {
            SimpleObject so = new SimpleObject();
            so.setCount(i);
            so.setValue("value " + i);
            so.setMyId("id_" + i);
            m.store(so);
        }
        while (m.getWriteBufferCount() != 0) Thread.sleep(100);
        assert (m.createQueryFor(SimpleObject.class).countAll() == 100);

        for (int i = 0; i < 100; i++) {
            SimpleObject so = new SimpleObject();
            so.setCount(i);
            so.setValue("value " + i);
            so.setMyId("id_" + i);
            m.store(so);
        }
        while (m.getWriteBufferCount() != 0) Thread.sleep(100);
        assert (m.createQueryFor(SimpleObject.class).countAll() == 100);
    }


    @WriteBuffer(size = 100, timeout = 1000)
    @Entity
    public static class SimpleObject {
        @Id
        private String myId;
        private String value;
        private int count;

        public String getMyId() {
            return myId;
        }

        public void setMyId(String myId) {
            this.myId = myId;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    @WriteBuffer(size = 10, timeout = 1000)
    public static class ComplexObjectBuffered extends ComplexObject {

    }


    @WriteBuffer(timeout = 5500)
    @NoCache
    public static class BufferedByTimeObject extends UncachedObject {

    }

    @WriteBuffer(size = 150, strategy = WriteBuffer.STRATEGY.IGNORE_NEW)
    public static class BufferedBySizeIgnoreNewObject extends UncachedObject {

    }

    @WriteBuffer(size = 150, strategy = WriteBuffer.STRATEGY.WRITE_NEW)
    public static class BufferedBySizeWriteNewObject extends UncachedObject {

    }

    @WriteBuffer(size = 150, strategy = WriteBuffer.STRATEGY.DEL_OLD)
    public static class BufferedBySizeDelOldObject extends UncachedObject {

    }

    @WriteBuffer(size = 150, timeout = 3000)
    public static class BufferedBySizeObject extends UncachedObject {

    }
}
