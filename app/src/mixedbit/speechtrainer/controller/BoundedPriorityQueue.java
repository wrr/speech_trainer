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

import java.util.LinkedList;
import java.util.Queue;
import java.util.TreeMap;

/**
 * Bounded queue that allows to efficiently retrieve the smallest element out of
 * maxQueueSize elements most recently added. Multiple equal elements can be
 * stored. add() and getMin() run in O(log(n)), where n is the smaller of
 * maxQueueSize and a current number of items in the queue.
 * 
 * The queue is used by the silence detection algorithm to find the smallest
 * sound level out of the most recent measurements of the sound level.
 */
class BoundedPriorityQueue<E extends Comparable<E>> {
    private final int maxQueueSize;
    // Maps an element to the number of occurrences of the element (at least 1).
    private final TreeMap<E, Integer> elementsMap = new TreeMap<E, Integer>();
    // Added elements in FIFO order.
    private final Queue<E> elementsList = new LinkedList<E>();

    /**
     * @param maxQueueSize
     *            upper bound on the queue size.
     */
    public BoundedPriorityQueue(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Adds an element to the queue. If the queue size exceeds the max size
     * defined in the constructor, removes an element that was added the longest
     * time ago. The queue can hold multiple equal elements.
     */
    public void add(E element) {
        elementsList.add(element);
        int addedElementCount = 1;
        if (elementsMap.containsKey(element)) {
            addedElementCount += elementsMap.get(element);
        }
        elementsMap.put(element, addedElementCount);

        if (elementsList.size() > maxQueueSize) {
            removeOldestElement();
        }
    }

    /**
     * @return the smallest element in the queue.
     */
    public E getMin() {
        return elementsMap.firstKey();
    }

    private void removeOldestElement() {
        final E removedElement = elementsList.remove();
        final int removedElementCount = elementsMap.get(removedElement);
        if (removedElementCount - 1 == 0) {
            elementsMap.remove(removedElement);
        } else {
            elementsMap.put(removedElement, removedElementCount - 1);
        }
    }
}