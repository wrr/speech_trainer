/**
 * This file is part of Speech Trainer.
 * Copyright (C) 2011 Jan Wrobel <wrr@mixedbit.org>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package mixedbit.speechtrainer.controller;

import mixedbit.speechtrainer.controller.BoundedPriorityQueue;
import junit.framework.TestCase;

public class BoundedPriorityQueueTest extends TestCase {

    public void testGetMinReturnsTheSmallestElement() {
        final BoundedPriorityQueue<Integer> queue = new BoundedPriorityQueue<Integer>(5);
        queue.add(4);
        queue.add(3);
        queue.add(7);
        assertEquals(3, queue.getMin().intValue());
    }

    public void testQueueMaxSizeRespected() {
        final int queueMaxSize = 10;
        final BoundedPriorityQueue<Integer> queue = new BoundedPriorityQueue<Integer>(queueMaxSize);
        for (int i = 0; i < queueMaxSize; ++i) {
            queue.add(i);
        }
        for (int i = 0; i < queueMaxSize; ++i) {
            queue.add(i + 10000);
        }
        // 2 * queueMaxSize elements were added to the queue. The oldest
        // elements (ones with values from 0 to queueMaxSize) should be removed.
        assertEquals(10000, queue.getMin().intValue());
    }

    public void multipleElementsWithTheSameKeyAllowed() {
        final int queueMaxSize = 10;
        final BoundedPriorityQueue<Integer> queue = new BoundedPriorityQueue<Integer>(queueMaxSize);
        // Add zero twice.
        queue.add(0);
        queue.add(0);
        // Fill the queue and make it remove the oldest element.
        for (int i = 1; i < queueMaxSize; ++i) {
            queue.add(i);
        }
        // Because two 0s were added to the queue, after one was removed, 0
        // should still be the smallest element.
        assertEquals(0, queue.getMin().intValue());
        // Add one more element and 0 should be removed from the queue.
        queue.add(15);
        assertEquals(1, queue.getMin().intValue());
    }
}
