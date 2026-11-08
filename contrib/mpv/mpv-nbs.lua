local JAR = "nbs-cli.jar"
local SOUNDS_DIR = "custom_instruments"
-- Passed to both nbs-cli and mpv, so they always agree on the raw audio format
local SAMPLE_RATE = "48000"
local CHANNELS = "stereo" -- mono or stereo
-- File extensions that are played through nbs-cli. It also supports txt, mid and midi,
-- but those are left out by default because other programs use them too
local EXTENSIONS = { nbs = true, mcsp = true, mcsp2 = true }

local function sh(s) return "'" .. s:gsub("'", "'\\''") .. "'" end

mp.add_hook("on_load", 50, function()
    local path = mp.get_property("stream-open-filename")
    if not path then return end
    local extension = path:match("%.([^./]+)$")
    if not extension or not EXTENSIONS[extension:lower()] then return end

    local fifo = os.tmpname()
    os.remove(fifo)
    os.execute("mkfifo " .. sh(fifo))
    os.execute(string.format("java -jar %s render -i %s -o - -of raw --pcm-encoding s16 --sample-rate %s --channels %s -q --stream --sounds-dir %s </dev/null >%s 2>/dev/null &",
                             sh(JAR), sh(path), sh(SAMPLE_RATE), sh(CHANNELS), sh(SOUNDS_DIR), sh(fifo)))

    mp.set_property("stream-open-filename", fifo)
    mp.set_property("file-local-options/demuxer", "rawaudio")
    mp.set_property("file-local-options/demuxer-rawaudio-rate", SAMPLE_RATE)
    mp.set_property("file-local-options/demuxer-rawaudio-channels", CHANNELS)
    mp.set_property("file-local-options/demuxer-rawaudio-format", "s16le") -- Matches --pcm-encoding s16

    -- Only clean up this file's FIFO once, instead of adding another handler for every loaded file
    local function cleanup()
        mp.unregister_event(cleanup)
        os.remove(fifo)
    end
    mp.register_event("end-file", cleanup)
end)
