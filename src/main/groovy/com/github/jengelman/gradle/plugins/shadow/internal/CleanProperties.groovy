/*
 * Source https://stackoverflow.com/a/39043903/519333
 */
package com.github.jengelman.gradle.plugins.shadow.internal

class CleanProperties extends Properties {
    private static class StripFirstLineStream extends FilterOutputStream {

        private boolean firstLineSeen = false

        StripFirstLineStream(final OutputStream out) {
            super(out)
        }

        @Override
        void write(final int b) throws IOException {
            if (firstLineSeen) {
                super.write(b);
            } else if (b == '\n') {
                super.write(b);

                firstLineSeen = true;
            }
        }

    }

    @Override
    void store(final OutputStream out, final String comments) throws IOException {
        super.store(new StripFirstLineStream(out), null)
    }
}
