# Java Color Management System (JCMS)
A Java library for easy management of ICC color transformations. It is basically a wrapper of [LittleCMS](http://www.littlecms.com) C library, with some helper classes.

*by Enric Tordera (etordera at gmail dot com)*

## Building JCMS
JCMS uses [gradle](http://gradle.org) as a build system. You will also need:
- Little CMS library (liblcms2-2)
- GCC compiler for C++ (g++)
- Java JDK

Once prerequisites are met, launch the build with:

    gradle build
    
This will generate the library as **JCMS.jar** inside *build/libs* directory.

## License

You are free to use, modify and distribute this software as you please. If you feel JCMS is useful for you, giving me credit would be great, but is not mandatory.

## Disclaimer

This software is provided "as is", without any warranty of any kind. If you decide to use it, you do it under your only responsibility. In no way will I be liable for any damage caused by this code.