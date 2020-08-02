## Do you need support?
Details about support for the project can be found here on the Wiki. You may also wish to use the [Subreddit](https://reddit.com/r/Dynmap/) or the [Discord](https://discord.gg/52pqBpw) to get support. Templates for asking support can be found in each location.

## Have you found a bug?
Before reporting a bug or issue, please make sure you can replicate the issue and that the issue is directly related to the main dynmap branch (not one of the forks like dynmap-essentials, dynmap-blockscanner, etc) If so, please submit bug reports **ONLY TO THIS GITHUB** with the title `[BUG REPORT] Bug Report Title` 

## Contributing to Dynmap's Code?
The Dynmap team welcomes Pull Requests with fixes, new features, and new platform support.  That said, the following rules apply:
- Ultimately, we reserve the right to accept or deny a PR for any reason: fact is, by accepting it, we're also accepting any of the problems with supporting it,
explaining it to users, and fixing current and future problems - if we don't think the PR is of value consistent with that cost, we'll probably not accept it.
- All PRs should be as small as they can be to accomplish the feature or fix being supplied.  To that end:
   - Do not lump multiple features into one PR - you'll be asked to split them up before they will be reviewed or accepted
   - Do not make style changes, reflow code, pretty printing, or otherwise make formatting-only code changes.  This makes the PR excessively large, 
   creating changes to be reviewed that don't actually do anything (but we have to review them to be sure they aren't being used to disguise security 
   compromises or other malicious code), and they create problems with the MANY people who fork Dynmap for the sake of doing PRs or their own private
   custom builds - since all theose modified lines create merge conflicts - once again, with no actual function having been accomplished.  If we decide
   the code needs to be 'prettied up', it'll be done by the Dynmap team.
- Do not make changes to core code (anything in DynmapCore or DynmapCoreAPI) unless you're ready to build and test it on all supported platforms.  Code that
breaks building of ANY supported platform will be rejected.
- Likewise, any Spigot related changes are expected to function correctly on all supported Spigot and PaperMC versions (currently 1.10.2 through 1.16.1).  
- Do not include any code that involves platform specific native libraries or command line behaviors.  Dynmap supports 32-bit and 64-bit, Windows, lots of
Linux versions (both x86 and ARM), MacOS, being used in Docker environments, and more - this is all about staying as 'pure Java' as the Minecraft server itself
is.  If your PR includes platform specific dependencies that are not coded to handle working on all the above platforms properly, the PR will be rejected.
- Dynmap's code is Apache Public License v2 - do not include any code that is not compatible with this license.  By contributing code, you are agreeing to
that code being subject to the APL v2.
- Do not include any code that unconditionally adds to Dynmap's hosting requirements - for example, support for a database can be added, but the use of the
database (which likely depends on a database server being deployed and configured by the user) cannot become an unconditional requirement in order to run
Dynmap.  Features can add the option to exploit new or additional technologies, but cannot add unconditionally to the minimum requirements on the supported
platforms (which is what is needed to run the corresponiding MC server, plus the Dynmap plugin or mod)
- Dynmap is built and supports running on Java 8 - it can run on newer versions, but any contributed code and dependencies MUST support being compiled and run
using just Java 8.
- Don't introduce other language depdendencies - Java only: no Kotlin, Scala, JRuby, whatever. They just add runtime dependencies that most of the platforms lack,
and language skills above and beyond the Java language requirements the code base already mandates, which just creates obstacles to other people contributing.
- Similarly, do not update existing libraries and dependencies - these are often tied to the versions on various platforms, and updates will likely break runtime
- Do not include code specific to other plugins or mods.  Dynmap has APIs for the purpose of avoiding the problem of working with other mods - there are many 
'Dynmap-XXX' mods and plugins which use the APIs to provide support for other mods and plugins (WorldGuard, Nucleus, Citizens, dozens of others).  Maintaining
interfaces in Dynmap particular to dozens of mods on multiple versions of multiple platforms is unmanageable, so we don't do it.  The ONLY exception currently
are security mods - although, even for those, leverage of platform-standard security interfaces is always preferred (e.g. Sponge or Bukket standard permissions)

## Porting, Supporting Other Platforms, Customized Dynmap Builds
While Dynmap is open source, subject to the Apache Public License, v2, the Dynmap team does have specific policies and requirements for anyone that would
use the code here for anything except building contributions submitted back to this code base as Pull Requests (which is the only process by which code is accepted and can become part of a release supported by the Dynmap team).  Other authorized uses include:

- Building custom version of Dynmap for use on a personal or on a specific server, so long as this version is NOT distributed to other parties. 
The modifying team agrees to not pursue support from the Dynmap team for this modified private version, but is otherwise not required to share the 
modified source code - though doing so is encouraged.
- Building a modified version of Dynmap for otherwise unsupported platforms: in this event, the modified version MUST be for a platform or version 
not yet (or no longer) supported by the Dynmap team.  If the Dynmap team comes to support this platform or version, the modifying team must agree to
cease distribution of the unofficial version, unless otherwise authorized to continue doing so.  Further:
    - The team distributing the modified version must cite the origin of the Dynmap code, but must also clearly indicate that the version is NOT supported by
    nor endorsed by the Dynmap team, and that ALL support should be directed through the team providing the modified version.
    - Any modified version CANNOT be monitized or otherwise charged for, under any circumstances, nor can redistribution of it be limited or restricted.
    - The modified code must continue to be Apache Public License v2, with no additional conditions or restrictions, including full public availability of the
    modified source code.
    - Any code from Dynmap used in such versions should be built from an appropriate fork, as DynampCore and other components (other than DynmapCoreAPI and 
    dynmap-api) are subject to breaking changes at any time, and the support messages in DynmapCore MUST be modified to refer to the supporting team (or, at
    least, removed).  The modified version should NOT refer to the Dynmap Discord nor to /r/Dynmap on Reddit for support. in any case.
    - Any bugs or issues opened in conjunction with the use of the modified version on this repository will be closed without comment.

Additions of new funtion, including new platform support, in this official Dynmap code base MUST be fully contained within the PRs submitted to this 
repository.  Further, it is always exptected than any updates will be built and tested across all relevant platforms - meaning anh chances to shared code 
components (DynmapCore, DynmapCoreAPI) MUST be successfully built and tested on ALL supported platforms (Forge, Spigot, etc).  Changes which break 
supported platforms will be rejected.

The only interfaces published and maintained as 'stable' are the interfaces of the DynmapCoreAPI (cross platform) and dynmap-api (Bukkit/spigot specific) 
libraries.  All other components are NOT libraries - DynmapCore, in particular, is a shared code component across the various platforms, but is subject to 
breaking changes without warning or consideration - any use of DynmapCore interfaces by code outside this repository is NOT supported, and will likely 
result in breaking of such consuming code without warning and without apology.  DynmapCore is an internal shared code component, not a library - please
treat it accordingly.

Plugins or mods using the published APIs - DynmapCoreAPI (for all platforms) or dynmap-api (only for Spigot/Bukkit) - may access these components as 
'compile' dependencies: DO NOT INTEGRATE THEM INTO YOUR PLUGIN - this will break Dynmap and/or other plugins when these interfaces are updated or 
expanded.  These libraries are published at https://repo.mikeprimm.com and will be updated each official release.

## Want to support the dynmap team?
I've set up a coffee-fund jar (I believe in the theory that software developers are machines that turn caffeine into code), for anyone who wants to throw in some tips!  I've got a Patreon here - https://www.patreon.com/dynmap, and for folks just looking to for a one-time coffee buy, hit my Ko-Fi at https://ko-fi.com/michaelprimm !
