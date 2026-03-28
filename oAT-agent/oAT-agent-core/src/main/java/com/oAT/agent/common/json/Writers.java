package com.oAT.agent.common.json;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * All custom writers for json-io subclass this class.  Special writers are not needed for handling
 * user-defined classes.  However, special writers are built/supplied by json-io for many of the
 * primitive types and other JDK classes simply to allow for a more concise form.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */
public class Writers
{
    private Writers () {}

    public static class TimeZoneWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            TimeZone cal = (TimeZone) obj;
            output.write("\"zone\":\"");
            output.write(cal.getID());
            output.write('"');
        }
        @Override
        public boolean hasPrimitiveForm() { return false; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) {}
    }

    public static class CalendarWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            Calendar cal = (Calendar) obj;
            SimpleDateFormat formatter = MetaUtils.dateFormat.get();
            formatter.setTimeZone(cal.getTimeZone());
            output.write("\"time\":\"");
            output.write(formatter.format(cal.getTime()));
            output.write("\",\"zone\":\"");
            output.write(cal.getTimeZone().getID());
            output.write('"');
        }
        @Override
        public boolean hasPrimitiveForm() { return false; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) {}
    }

    public static class DateWriter implements JsonWriter.JsonClassWriter, JsonWriter.JsonClassWriterEx
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            throw new JsonIoException("Should never be called.");
        }
        @Override
        public void write(Object obj, boolean showType, Writer output, Map args) throws IOException
        {
            Date date = (Date) obj;
            Object dateFormat = args.get(DATE_FORMAT);
            if (dateFormat instanceof String)
            {   // Passed in as String, turn into a SimpleDateFormat instance to be used throughout this stream write.
                dateFormat = new SimpleDateFormat((String) dateFormat, Locale.ENGLISH);
                args.put(DATE_FORMAT, dateFormat);
            }
            if (showType)
            {
                output.write("\"value\":");
            }

            if (dateFormat instanceof SimpleDateFormat)
            {
                output.write("\"");
                output.write(((SimpleDateFormat) dateFormat).format(date));
                output.write("\"");
            }
            else
            {
                output.write(Long.toString(date.getTime()));
            }
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) {
            throw new JsonIoException("Should never be called.");
        }
        public void writePrimitiveForm(Object o, Writer output, Map args) throws IOException
        {
            if (args.containsKey(DATE_FORMAT))
            {
                write(o, false, output, args);
            }
            else
            {
                output.write(Long.toString(((Date) o).getTime()));
            }
        }
    }

    public static class TimestampWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object o, boolean showType, Writer output) throws IOException
        {
            Timestamp tstamp = (Timestamp) o;
            output.write("\"time\":\"");
            output.write(Long.toString((tstamp.getTime() / 1000) * 1000));
            output.write("\",\"nanos\":\"");
            output.write(Integer.toString(tstamp.getNanos()));
            output.write('"');
        }
        @Override
        public boolean hasPrimitiveForm() { return false; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) { }
    }

    public static class ClassWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            String value = ((Class) obj).getName();
            output.write("\"value\":");
            writeJsonUtf8String(value, output);
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            writeJsonUtf8String(((Class)o).getName(), output);
        }
    }

    public static class JsonStringWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            output.write("\"value\":");
            writeJsonUtf8String((String) obj, output);
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            writeJsonUtf8String((String) o, output);
        }
    }

    public static class LocaleWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            Locale locale = (Locale) obj;

            output.write("\"language\":\"");
            output.write(locale.getLanguage());
            output.write("\",\"country\":\"");
            output.write(locale.getCountry());
            output.write("\",\"variant\":\"");
            output.write(locale.getVariant());
            output.write('"');
        }
        @Override
        public boolean hasPrimitiveForm() { return false; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) { }
    }

    public static class BigIntegerWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            if (showType)
            {
                BigInteger big = (BigInteger) obj;
                output.write("\"value\":\"");
                output.write(big.toString(10));
                output.write('"');
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            BigInteger big = (BigInteger) o;
            output.write('"');
            output.write(big.toString(10));
            output.write('"');
        }
    }

    public static class AtomicBooleanWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            if (showType)
            {
                AtomicBoolean value = (AtomicBoolean) obj;
                output.write("\"value\":");
                output.write(value.toString());
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            AtomicBoolean value = (AtomicBoolean) o;
            output.write(value.toString());
        }
    }

    public static class AtomicIntegerWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            if (showType)
            {
                AtomicInteger value = (AtomicInteger) obj;
                output.write("\"value\":");
                output.write(value.toString());
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            AtomicInteger value = (AtomicInteger) o;
            output.write(value.toString());
        }
    }

    public static class AtomicLongWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            if (showType)
            {
                AtomicLong value = (AtomicLong) obj;
                output.write("\"value\":");
                output.write(value.toString());
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            AtomicLong value = (AtomicLong) o;
            output.write(value.toString());
        }
    }

    public static class BigDecimalWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            if (showType)
            {
                BigDecimal big = (BigDecimal) obj;
                output.write("\"value\":\"");
                output.write(big.toPlainString());
                output.write('"');
            }
            else
            {
                writePrimitiveForm(obj, output);
            }
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            BigDecimal big = (BigDecimal) o;
            output.write('"');
            output.write(big.toPlainString());
            output.write('"');
        }
    }

    public static class StringBuilderWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            StringBuilder builder = (StringBuilder) obj;
            output.write("\"value\":\"");
            output.write(builder.toString());
            output.write('"');
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            StringBuilder builder = (StringBuilder) o;
            output.write('"');
            output.write(builder.toString());
            output.write('"');
        }
    }

    public static class StringBufferWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object obj, boolean showType, Writer output) throws IOException
        {
            StringBuffer buffer = (StringBuffer) obj;
            output.write("\"value\":\"");
            output.write(buffer.toString());
            output.write('"');
        }
        @Override
        public boolean hasPrimitiveForm() { return true; }
        @Override
        public void writePrimitiveForm(Object o, Writer output) throws IOException
        {
            StringBuffer buffer = (StringBuffer) o;
            output.write('"');
            output.write(buffer.toString());
            output.write('"');
        }
    }

    // ========== Maintain knowledge about relationships below this line ==========
    static final String DATE_FORMAT = JsonWriter.DATE_FORMAT;

    protected static void writeJsonUtf8String(String s, final Writer output) throws IOException
    {
        JsonWriter.writeJsonUtf8String(s, output);
    }
}
