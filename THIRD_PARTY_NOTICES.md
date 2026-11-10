# Third-party components

The MIT license in this repository covers DoT Pet's original code, documentation,
and bundled slime artwork. The maintainer confirmed redistribution rights for the
slime artwork. Third-party components retain their own copyrights and licenses.

| Component | Version | License / notices |
| --- | --- | --- |
| Eclipse Temurin / OpenJDK | Java 21 | GPL v2 with Classpath Exception; notices in the bundled `runtime/legal/` directory. [Source](https://github.com/adoptium/jdk21u), [binary releases](https://github.com/adoptium/temurin21-binaries). |
| OpenJFX / JavaFX | 21.0.13 | GPL v2 with Classpath Exception; [license and additional notices](https://github.com/openjdk/jfx/tree/jfx21/doc-files). [Source](https://github.com/openjdk/jfx/tree/jfx21). |
| JNA / JNA Platform | 5.19.1 | Dual licensed under LGPL 2.1 or later / Apache License 2.0. The original `META-INF/LICENSE` is retained in both JARs. [License](https://github.com/java-native-access/jna/blob/5.19.1/LICENSE). |
| Microsoft runtime DLLs bundled by OpenJFX | As supplied upstream | Upstream redistributable components; original files and signatures are retained. They are not relicensed under MIT. |

Build infrastructure (Gradle, OpenJFX Gradle plugin, GitHub Actions) is separate
from the installed application and retains its upstream licenses.

Code signing does not change the origin or licenses of these dependencies.
DoT Pet will not sign upstream native libraries under the SignPath Foundation
project certificate contrary to its [terms](https://signpath.org/terms).
