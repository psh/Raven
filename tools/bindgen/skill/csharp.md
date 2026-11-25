A test in any runner (xUnit here), calling the driver through `Raven.R`:

```csharp
[Fact]
public void AddsANote()
{
    var desc = new R.RInitDesc { backend = R.R_BACKEND_NULL, headless = 1 };
    Assert.True(R.Rinit_ex(ref desc) != 0, "Raven didn't start");
    try
    {
        R.Rset_animation_time_step(1.0 / 60);
        Notes.Open("notes.xml");                            // the app's code: builds the window, binds its actions

        ulong Find(string selector)
        {
            R.Rwait_idle(600);
            ulong found = R.Raccessible_find(0, selector);
            Assert.True(found != 0, selector + ": " + R.Raccessible_get_last_error());
            return found;
        }
        void Acted(int done)
        {
            Assert.True(done != 0, R.Raccessible_get_last_error());
            R.Rwait_idle(600);
        }
        Acted(R.Raccessible_type(Find("role=text-field"), "buy milk"));
        Acted(R.Raccessible_click(Find("role=button name=\"Add\"")));
        Assert.Equal(1, R.Raccessible_count(0, "role=list-item text=\"buy milk\""));
    }
    finally
    {
        R.Rshutdown();
    }
}
```

Put the library beside the test assembly, as beside a program.
