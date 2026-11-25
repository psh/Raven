A plain script (or a busted test), calling the driver through `raven.lua`; texts from Raven are
`char*`, read with `ffi.string`:

```lua
local r = require("raven")
local ffi = r.ffi

local desc = ffi.new("RInitDesc")
desc.backend = r.R_BACKEND_NULL
desc.headless = 1
assert(r.Rinit_ex(desc) ~= 0, "Raven didn't start")
r.Rset_animation_time_step(1 / 60)
open_notes("notes.xml") -- the app's code: builds the window, binds its actions

local function find(selector)
  r.Rwait_idle(600)
  local found = r.Raccessible_find(0, selector)
  assert(found ~= 0ULL, selector .. ": " .. ffi.string(r.Raccessible_get_last_error()))
  return found
end
local function acted(done)
  assert(done ~= 0, ffi.string(r.Raccessible_get_last_error()))
  r.Rwait_idle(600)
end

acted(r.Raccessible_type(find("role=text-field"), "buy milk"))
acted(r.Raccessible_click(find('role=button name="Add"')))
assert(r.Raccessible_count(0, 'role=list-item text="buy milk"') == 1, 'no note "buy milk"')
r.Rshutdown()
```

`RAVEN_LIBRARY=/path/to/libraven.so luajit test_notes.lua`
