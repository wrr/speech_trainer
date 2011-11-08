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

package mixedbit.speechtrainer;

import junit.framework.TestCase;

public class AssertionsTest extends TestCase {

    public void testAssertionError() {
        Assertions.check(true);
        try {
            Assertions.check(false);
            fail();
        } catch (final AssertionError e) {
            // expected;
        }
    }

    public void testIllegalStateError() {
        Assertions.illegalStateIfFalse(true);
        Assertions.illegalStateIfFalse(true, "Error message.");
        try {
            Assertions.illegalStateIfFalse(false);
            fail();
        } catch (final IllegalStateException e) {
            // expected;
        }
        try {
            Assertions.illegalStateIfFalse(false, "Error message.");
            fail();
        } catch (final IllegalStateException e) {
            // expected;
        }
    }
}
