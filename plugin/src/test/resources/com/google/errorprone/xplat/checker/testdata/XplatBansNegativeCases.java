// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.errorprone.xplat.checker.testdata;

import com.google.errorprone.xplat.checker.XplatBanSuppression;
import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.MutableDateTime;
import org.joda.time.tz.FixedDateTimeZone;

public class XplatBansNegativeCases {

  public class aClass {

    // test allowed field
    private DateTime time;

    // test allowed constructor
    public aClass(DateTime time) {
      this.time = time;
    }

    // test allowed method
    private DateTime getTime(DateTime time) {
      return time;
    }
  }

  // allowed function
  private static DateTime goodLocalDateTimeUse() {
    LocalDateTime ldt = new LocalDateTime(2020, 6, 2, 8, 0, 0, 0);
    return new DateTime(ldt.getYear(), ldt.getMonthOfYear(), ldt.getDayOfYear(), ldt.getHourOfDay(),
        ldt.getMinuteOfHour(), ldt.getSecondOfMinute(), ldt.getMillisOfSecond(),
        DateTimeZone.forID("America/New_York"))
        .toDateTime(DateTimeZone.forID("America/Los_Angeles"));

  }

  public static void main(String[] args) {
    // test allowed new
    DateTime dt = new DateTime();

    DateTime dt2 = goodLocalDateTimeUse();

    // test allowed method call
    dt2.dayOfYear();

    // test allowed local var
    DateTime dt3;

    System.out.println(dt.toString());
  }

  // Test annotation suppression
  private class Illegal {

    @XplatBanSuppression
    private Chronology chrono;

    @XplatBanSuppression
    public Illegal(Chronology chrono) {
      this.chrono = chrono;
    }

    @XplatBanSuppression
    private Chronology returnChrono() {
      return this.chrono;
    }
  }

  // Test annotation suppression
  @XplatBanSuppression
  private class Illegal2 {

    private FixedDateTimeZone time;

    public Illegal2(FixedDateTimeZone time) {
      this.time = time;
    }

    private FixedDateTimeZone returnChrono() {
      return this.time;
    }
  }

  // Test annotation suppression
  @XplatBanSuppression
  public void tzTime() {
    FixedDateTimeZone time = new FixedDateTimeZone("1", "1", 1, 1);

    time.getStandardOffset(1);
  }

  // Test annotation suppression
  public void mutableDate() {

    @XplatBanSuppression
    MutableDateTime dateTime = new MutableDateTime();

    @XplatBanSuppression
    MutableDateTime time;

  }

}
