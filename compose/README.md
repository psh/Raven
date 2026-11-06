# Compose Bindings

Compose runtime sits below UI, Foundation and Material.  Projects like "mosaic" build on Compose runtime
to provide a custom rendering (mosaic offers text in a terminal).

## Compose UI

Today, compose-ui is tightly bound to the Skia rendering engine, and that means the standard "foundation"
components are also tightly bound to Skia

## Raven

See: https://github.com/oscar-soirey/Raven

Today, Raven allows code (in C) or XML to define the UI.  It would be natural to add a "compose-raven"
layer that maps between the core compose runtime and the Raven widgets and rendering, mimicing the 
current compose-ui layer, using mosaic as a template / role-model to follow.
