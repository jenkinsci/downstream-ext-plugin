Fork of the downstream_ext jenkins plugin. 

It allows the additional configuration to build downstream projects only when the current project has SCM changes. 

This seems to be a typical use case:
* a code change in the local module can affect dependent modules
* even when they contain no changes
* but this dependency should no travel further down the dependencies

Right now the downstream projects would be built only if they contain changes also, which is not what is needed in these cases.