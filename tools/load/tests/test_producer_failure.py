import asyncio
import tempfile
import time
import unittest

from tools.load.transport import Transport


class ProducerFailureTest(unittest.IsolatedAsyncioTestCase):
    async def test_actual_full_capture_queue_fails_consumer_without_waiting_for_sentinel(self):
        with tempfile.TemporaryDirectory() as directory:
            transport = Transport(dict(durationSeconds=4, chunkMs=2000), directory)
            transport.epoch = time.monotonic() - 4
            recorder = dict(recorderId="producer", proofs=[], sentChunks=0, sampleCount=0, errors=0)
            queue = asyncio.Queue(maxsize=1)
            producer = asyncio.create_task(transport.produce(recorder, queue))
            result = await asyncio.gather(producer, return_exceptions=True)
            self.assertIsInstance(result[0], asyncio.QueueFull)
            self.assertEqual(2, len(recorder["proofs"]))
            self.assertEqual(1, recorder["sentChunks"])
            with self.assertRaises(asyncio.QueueFull):
                await asyncio.wait_for(transport.next_produced(queue, producer), .2)
            self.assertEqual(1, queue.qsize(), "Failed capture must not silently drain partial audio")

    async def test_completed_producer_still_delivers_every_queued_item_then_end(self):
        with tempfile.TemporaryDirectory() as directory:
            transport = Transport({}, directory)
            queue = asyncio.Queue(maxsize=2)
            async def produce():
                await queue.put(("original", b"pcm", 0))
                await queue.put(None)
            producer = asyncio.create_task(produce())
            await producer
            self.assertEqual(("original", b"pcm", 0), await transport.next_produced(queue, producer))
            self.assertIsNone(await transport.next_produced(queue, producer))

    async def test_waiting_consumer_observes_producer_failure_and_cleans_its_queue_waiter(self):
        with tempfile.TemporaryDirectory() as directory:
            transport = Transport({}, directory)
            queue = asyncio.Queue(maxsize=1)
            release = asyncio.Event()
            async def produce():
                await release.wait()
                raise RuntimeError("capture failed")
            producer = asyncio.create_task(produce())
            consumer = asyncio.create_task(transport.next_produced(queue, producer))
            await asyncio.sleep(0)
            release.set()
            with self.assertRaisesRegex(RuntimeError, "capture failed"):
                await asyncio.wait_for(consumer, .2)
            await queue.put("retained")
            self.assertEqual("retained", await asyncio.wait_for(queue.get(), .2))
            await asyncio.gather(producer, return_exceptions=True)

    async def test_cancelled_consumer_does_not_cancel_its_producer_or_leave_a_queue_waiter(self):
        with tempfile.TemporaryDirectory() as directory:
            transport = Transport({}, directory)
            queue = asyncio.Queue(maxsize=1)
            producer = asyncio.create_task(asyncio.Event().wait())
            consumer = asyncio.create_task(transport.next_produced(queue, producer))
            await asyncio.sleep(0)
            consumer.cancel()
            with self.assertRaises(asyncio.CancelledError):
                await consumer
            self.assertFalse(producer.done())
            await queue.put("retained")
            self.assertEqual("retained", await asyncio.wait_for(queue.get(), .2))
            producer.cancel()
            await asyncio.gather(producer, return_exceptions=True)
