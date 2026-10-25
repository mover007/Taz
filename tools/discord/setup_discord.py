"""Builds the Taz community Discord server with a bot (Discord doesn't allow scripting a user account).

What it sets up (nothing is deleted; anything that already exists is kept):
  roles       Taz Dev, Moderator, Beta Tester, Updates Ping, Forager, Dungeons, SkyBlock XP
  channels    welcome, rules, announcements, roles / download, features, changelog, commits /
              general, showcase, screenshots, tree-routes / help, bug-reports, suggestions /
              beta (Beta Testers only) / voice / staff (Moderators only)
  forums      screenshots, tree-routes, help, bug-reports and suggestions are forums with tags
  posts       welcome (with the banner), rules, download, features, roles and a pinned
              "how to share a route" post in tree-routes
  server      the Taz icon, Community mode, a welcome screen and onboarding questions that hand out roles
Your existing #general, General voice and commits channels are renamed and moved into place, so their
history and the GitHub webhook keep working.

Run:  python setup_discord.py            (shows what it would do, changes nothing)
      python setup_discord.py --apply    (does it)
The bot token is asked for without echoing it (or read from DISCORD_BOT_TOKEN); it isn't saved anywhere.
"""
import base64
import getpass
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

API = os.environ.get("TAZ_DISCORD_API", "https://discord.com/api/v10")
HERE = os.path.dirname(os.path.abspath(__file__))
BRANDING = os.path.normpath(os.path.join(HERE, "..", "..", "branding"))
USER_AGENT = "DiscordBot (https://github.com/mover007/Taz, 1.0)"

# ---- permissions (bit flags)
ADMINISTRATOR = 1 << 3
KICK, BAN = 1 << 1, 1 << 2
ADD_REACTIONS = 1 << 6
VIEW_AUDIT_LOG = 1 << 7
VIEW = 1 << 10
SEND = 1 << 11
MANAGE_MESSAGES = 1 << 13
READ_HISTORY = 1 << 16
MUTE_MEMBERS, MOVE_MEMBERS = 1 << 22, 1 << 24
MANAGE_NICKNAMES = 1 << 27
MANAGE_THREADS = 1 << 34
CREATE_PUBLIC_THREADS, CREATE_PRIVATE_THREADS = 1 << 35, 1 << 36
SEND_IN_THREADS = 1 << 38
MODERATE_MEMBERS = 1 << 40
MOD_PERMS = KICK | BAN | VIEW_AUDIT_LOG | MANAGE_MESSAGES | MUTE_MEMBERS | MOVE_MEMBERS | MANAGE_NICKNAMES | MANAGE_THREADS | MODERATE_MEMBERS

TEXT, VOICE, CATEGORY, ANNOUNCEMENT, FORUM = 0, 2, 4, 5, 15

# ---- the design

ROLES = [  # name, colour, shown separately, mentionable, permissions
    ("Taz Dev", 0x4FD1B0, True, False, ADMINISTRATOR),
    ("Moderator", 0x2FBF71, True, False, MOD_PERMS),
    ("Beta Tester", 0xFFD75E, True, False, 0),
    ("Updates Ping", 0x6EE79A, False, True, 0),
    ("Forager", 0x8FE3C0, False, False, 0),
    ("Dungeons", 0xB48CFF, False, False, 0),
    ("SkyBlock XP", 0x5BC0FF, False, False, 0),
]

# access: "public", "read-only", "showcase" (read-only, comments in threads), "beta", "beta-read-only", "staff"
LAYOUT = [
    ("🌳 WELCOME", "public", [
        ("👋・welcome", TEXT, "read-only", "Start here: what Taz is and where everything is."),
        ("📜・rules", TEXT, "read-only", "Read these before chatting."),
        ("📣・announcements", TEXT, "read-only", "Releases and big news. Follow this channel to get it in your own server."),
        ("🎭・roles", TEXT, "read-only", "What the roles mean. Change yours in Channels & Roles at the top of the list."),
    ]),
    ("🪓 TAZ", "public", [
        ("⬇️・download", TEXT, "read-only", "How to install Taz, and the latest build."),
        ("✨・features", TEXT, "read-only", "Everything Taz does."),
        ("📝・changelog", TEXT, "read-only", "What changed in each version."),
        ("🔨・commits", TEXT, "read-only", "Every change to the code, live from GitHub."),
    ]),
    ("💬 COMMUNITY", "public", [
        ("💬・general", TEXT, "public", "Talk about Taz, SkyBlock, anything."),
        ("🎬・showcase", TEXT, "showcase", "New features in action. React, or reply in a thread."),
        ("📸・screenshots", FORUM, "public", "Show your Taz setup: HUD layouts, routes, drops. One post per screenshot or clip."),
        ("🌲・tree-routes", FORUM, "public", "Share tree routes: paste the [Skyblocker-Waypoint-Data-V1] export so anyone can import it "
                                          "(/taz > Galatea > Tree Route Assist > Import from clipboard). Say the island and your lap time."),
    ]),
    ("🛠️ SUPPORT", "public", [
        ("❓・help", FORUM, "public", "Stuck? Make a post: what you tried, your Minecraft and Fabric versions, and a screenshot. "
                                    "Tag it Solved when you're sorted."),
        ("🐛・bug-reports", FORUM, "public", "One bug per post: what happened, what you expected, how to make it happen, "
                                           "and your logs/latest.log if it crashed."),
        ("💡・suggestions", FORUM, "public", "One idea per post. React with 👍 on the ideas you want."),
    ]),
    ("🧪 BETA", "beta", [
        ("🧪・beta-builds", TEXT, "beta-read-only", "Test builds before release."),
        ("💬・beta-chat", TEXT, "beta", "Feedback on test builds."),
    ]),
    ("🔊 VOICE", "public", [
        ("🔊・hangout", VOICE, "public", None),
        ("🎥・streaming", VOICE, "public", None),
    ]),
    ("🛡️ STAFF", "staff", [
        ("🛡️・mod-chat", TEXT, "staff", "Moderators only."),
        ("📋・mod-log", TEXT, "staff", "Discord's community updates and moderation notes."),
    ]),
]

FORUM_SETUP = {
    "📸・screenshots": {"tags": [("HUD", "🖥️"), ("Route", "🌲"), ("Drop", "💎"), ("Clip", "🎥")], "reaction": "🔥", "layout": 2},
    "🌲・tree-routes": {"tags": [("Torrhus Canyon", "🏜️"), ("Moonglade Marsh", "🌙"), ("Other island", "🗺️"), ("Fast", "⚡")],
                        "reaction": "🌲", "layout": 1},
    "❓・help": {"tags": [("Install", "⬇️"), ("Settings", "⚙️"), ("Question", "❓"), ("Solved", "✅")], "reaction": None, "layout": 1},
    "🐛・bug-reports": {"tags": [("Open", "🔴"), ("Confirmed", "🟡"), ("Fixed", "🟢"), ("Can't reproduce", "⚪")], "reaction": None,
                        "layout": 1},
    "💡・suggestions": {"tags": [("Idea", "💡"), ("Planned", "🗓️"), ("Added", "✅"), ("Not planned", "❌")], "reaction": "👍",
                        "layout": 1},
}

# Existing channels to reuse (keeps their history and webhooks): match -> new name
REUSE = [
    (lambda c: c["type"] == TEXT and c["name"] == "general", "💬・general"),
    (lambda c: c["type"] == VOICE and c["name"].lower() == "general", "🔊・hangout"),
    (lambda c: c["type"] == TEXT and ("commit" in c["name"] or "github" in c["name"]) and c["name"] != "🔨・commits", "🔨・commits"),
]

GREEN, TEAL, GOLD = 0x2FBF71, 0x4FD1B0, 0xFFD75E

POSTS = {
    "👋・welcome": {"file": "taz-discord-banner-960x540.png", "embeds": [{
        "title": "Welcome to Taz 🌳",
        "description": ("**Taz** is a Hypixel SkyBlock mod for Fabric (Minecraft 26.1.2): one mod, one settings menu "
                        "(`/taz`), and every feature off until you turn it on.\n\n"
                        "Built for **Galatea and Torrhus Canyon foraging** first, with dungeons, SkyBlock XP and plenty "
                        "of everyday helpers on top."),
        "color": GREEN,
        "image": {"url": "attachment://taz-discord-banner-960x540.png"},
        "fields": [
            {"name": "🚀 Get started", "value": "{download} to install\n{features} for what it does", "inline": True},
            {"name": "💬 Hang out", "value": "{general} to chat\n{screenshots} to show your setup", "inline": True},
            {"name": "🌲 Routes", "value": "{tree_routes} to share and grab tree routes", "inline": True},
            {"name": "🛠️ Need help?", "value": "{help} for questions\n{bug_reports} for bugs\n{suggestions} for ideas", "inline": True},
            {"name": "📣 Stay updated", "value": "{announcements} for releases\n{commits} for every change", "inline": True},
            {"name": "🎭 Roles", "value": "Pick yours in **Channels & Roles** at the top of the channel list.", "inline": True},
        ],
    }]},
    "📜・rules": {"embeds": [{
        "title": "📜 Rules",
        "description": "\n\n".join([
            "**1. Be kind.** No harassment, hate speech or drama. Disagree with ideas, not people.",
            "**2. No spam or ads.** No self-promo, invite links or selling (accounts, items, coins, services).",
            "**3. Keep it legit.** Taz only does what Hypixel allows. No macros, cheats or ways around Hypixel's rules, "
            "and don't ask for them.",
            "**4. Right channel, right topic.** Questions go in {help}, bugs in {bug_reports}, ideas in {suggestions}.",
            "**5. Keep it clean.** No NSFW, gore or shock content, including names and avatars.",
            "**6. English in public channels**, so everyone can follow and moderate.",
            "**7. Follow Discord's Terms and Community Guidelines.** Moderators have the final say.",
        ]),
        "color": TEAL,
        "footer": {"text": "Breaking the rules gets a warning, a timeout or a ban, depending on how bad it is."},
    }]},
    "⬇️・download": {"embeds": [{
        "title": "⬇️ Install Taz",
        "description": ("Taz isn't public yet. When it is, the latest build goes right here and in {announcements}.\n\n"
                        "**You'll need**\n"
                        "• Minecraft **26.1.2** with **Fabric Loader 0.19.5+**\n"
                        "• **Fabric API** and **Fabric Language Kotlin**\n\n"
                        "**Then**\n"
                        "1. Put `taz-x.y.z.jar` in your `mods` folder.\n"
                        "2. Start the game and type **`/taz`**: everything is in that one menu.\n"
                        "3. Every feature starts **off**, so turn on what you want. Settings save by themselves.\n\n"
                        "HUDs move with **Right Shift**. Stuck? Ask in {help}."),
        "color": GREEN,
    }]},
    "✨・features": {"embeds": [{
        "title": "✨ What Taz does",
        "color": GREEN,
        "fields": [
            {"name": "🪓 Galatea & Torrhus Canyon", "value": (
                "• **Tree Route Assist**: Skyblocker routes, training mode with laps and splits\n"
                "• Next-tree pointer, tree progress, sweep details\n"
                "• Tree Gifts & loot tracker, whisper rates\n"
                "• Honeyhives, Ruby Veilshroom finder, pressure warning\n"
                "• Starlyn contest timer and score\n"
                "• Sea Lumies, hunting mob glows, Fairy Souls, island guide"), "inline": False},
            {"name": "⚔️ Dungeons", "value": "Run timer and end-time prediction, live score, splits, PBs, party messages", "inline": False},
            {"name": "✨ SkyBlock XP", "value": "Cheapest XP upgrades, level plan route, NPC tracker", "inline": False},
            {"name": "🧭 Everywhere", "value": ("Alerts, Dark Auction reminder, chat filter, command keys, event timers, "
                                               "skill tracker, chat waypoints, price tooltips, rarity backgrounds, slayer and fishing helpers"),
             "inline": False},
        ],
        "footer": {"text": "Every feature is off until you turn it on in /taz."},
    }]},
    "🎭・roles": {"embeds": [{
        "title": "🎭 Roles",
        "description": ("{r_dev} makes Taz.\n"
                        "{r_mod} keeps things friendly.\n"
                        "{r_beta} tries builds before release, in the Beta channels.\n\n"
                        "**Pick your own** in **Channels & Roles** (top of the channel list):\n"
                        "{r_ping} get pinged for releases\n"
                        "{r_forager} {r_dungeons} {r_xp} show what you play"),
        "color": GOLD,
    }]},
}

ROUTE_GUIDE = {
    "name": "📌 How to share a tree route",
    "content": ("**Sharing a route**\n"
                "1. In Taz: `/taz` > Galatea > Tree Route Assist > **Export to clipboard** (or export it from Skyblocker).\n"
                "2. Make a post here: put the island in the title, tag it, and paste the export "
                "(`[Skyblocker-Waypoint-Data-V1]...`).\n"
                "3. Add your best lap time and a screenshot if you have one.\n\n"
                "**Using someone's route**\n"
                "Copy their export, stand on that island, then `/taz` > Galatea > Tree Route Assist > **Import from clipboard**. "
                "Turn on **Training** mode to learn it with a timer."),
}


# ---- Discord API

class Discord:
    def __init__(self, token, apply):
        self.token = token
        self.apply = apply

    def call(self, method, path, body=None, files=None, write=True):
        if write and not self.apply:
            return None
        url = API + path
        headers = {"Authorization": "Bot " + self.token, "User-Agent": USER_AGENT}
        data = None
        if files:
            boundary = uuid.uuid4().hex
            parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="payload_json"\r\n'
                     f'Content-Type: application/json\r\n\r\n{json.dumps(body)}\r\n'.encode("utf-8")]
            for i, (name, content) in enumerate(files):
                parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="files[{i}]"; filename="{name}"\r\n'
                             f'Content-Type: image/png\r\n\r\n'.encode("utf-8") + content + b"\r\n")
            parts.append(f"--{boundary}--\r\n".encode("utf-8"))
            data = b"".join(parts)
            headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
        elif body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        for _ in range(6):
            req = urllib.request.Request(url, data=data, method=method, headers=headers)
            try:
                with urllib.request.urlopen(req) as resp:
                    raw = resp.read()
                    return json.loads(raw) if raw else None
            except urllib.error.HTTPError as e:
                raw = e.read()
                if e.code == 429:
                    wait = json.loads(raw or b"{}").get("retry_after", 2)
                    time.sleep(float(wait) + 0.25)
                    continue
                raise RuntimeError(f"{method} {path} -> {e.code}: {raw.decode('utf-8', 'replace')[:300]}")
        raise RuntimeError(f"{method} {path}: still rate limited")

    def get(self, path):
        return self.call("GET", path, write=False)


_snow = [0]


def snowflake():
    _snow[0] += 1
    return str(((int(time.time() * 1000) - 1420070400000) << 22) + _snow[0])


def overwrites(access, everyone, roles):
    dev, mod, beta = roles["Taz Dev"], roles["Moderator"], roles["Beta Tester"]

    def ow(rid, allow=0, deny=0):
        return {"id": rid, "type": 0, "allow": str(allow), "deny": str(deny)}

    if access == "public":
        return []
    if access == "read-only":
        return [ow(everyone, ADD_REACTIONS | READ_HISTORY, SEND | CREATE_PUBLIC_THREADS | CREATE_PRIVATE_THREADS),
                ow(dev, SEND), ow(mod, SEND)]
    if access == "showcase":
        return [ow(everyone, ADD_REACTIONS | READ_HISTORY | CREATE_PUBLIC_THREADS | SEND_IN_THREADS, SEND),
                ow(dev, SEND)]
    if access == "beta":
        return [ow(everyone, 0, VIEW), ow(beta, VIEW | SEND), ow(mod, VIEW | SEND), ow(dev, VIEW | SEND)]
    if access == "beta-read-only":
        return [ow(everyone, 0, VIEW), ow(beta, VIEW | READ_HISTORY | ADD_REACTIONS, SEND), ow(mod, VIEW | SEND), ow(dev, VIEW | SEND)]
    if access == "staff":
        return [ow(everyone, 0, VIEW), ow(mod, VIEW | SEND), ow(dev, VIEW | SEND)]
    raise ValueError(access)


def fill(text, links):
    for key, value in links.items():
        text = text.replace("{" + key + "}", value)
    return text


def fill_embed(obj, links):
    if isinstance(obj, str):
        return fill(obj, links)
    if isinstance(obj, list):
        return [fill_embed(o, links) for o in obj]
    if isinstance(obj, dict):
        return {k: fill_embed(v, links) for k, v in obj.items()}
    return obj


def pick_guild(d, me):
    while True:
        guilds = d.get("/users/@me/guilds")
        if len(guilds) == 1:
            return guilds[0]
        if guilds:
            for i, g in enumerate(guilds, 1):
                print(f"  {i}. {g['name']}")
            choice = input("Which server? Number: ").strip()
            if choice.isdigit() and 1 <= int(choice) <= len(guilds):
                return guilds[int(choice) - 1]
            continue
        print("\nThe bot isn't in your server yet. Open this link, pick your server and press Authorize:")
        print(f"  https://discord.com/oauth2/authorize?client_id={me['id']}&scope=bot&permissions={ADMINISTRATOR}")
        input("Then press Enter here... ")


def main():
    apply = "--apply" in sys.argv
    token = os.environ.get("DISCORD_BOT_TOKEN") or getpass.getpass("Bot token (it won't show while you paste): ").strip()
    d = Discord(token, apply)
    me = d.get("/users/@me")
    print(f"Logged in as bot {me['username']}.")
    guild = d.get(f"/guilds/{pick_guild(d, me)['id']}")
    gid = guild["id"]
    print(f"Server: {guild['name']}" + ("" if apply else "   (dry run: nothing will change; add --apply to do it)"))
    log = []

    def did(msg):
        log.append(msg)
        print(("  + " if apply else "  would ") + msg)

    # roles
    existing_roles = {r["name"]: r["id"] for r in d.get(f"/guilds/{gid}/roles")}
    roles = {}
    for name, color, hoist, mentionable, perms in ROLES:
        if name in existing_roles:
            roles[name] = existing_roles[name]
            continue
        created = d.call("POST", f"/guilds/{gid}/roles", {"name": name, "color": color, "hoist": hoist,
                                                          "mentionable": mentionable, "permissions": str(perms)})
        roles[name] = created["id"] if created else f"<{name}>"
        did(f"role {name}")
    if apply and guild.get("owner_id"):
        d.call("PUT", f"/guilds/{gid}/members/{guild['owner_id']}/roles/{roles['Taz Dev']}")
        did("give the server owner Taz Dev")

    # reuse channels people already have (keeps history and the GitHub webhook)
    channels = d.get(f"/guilds/{gid}/channels")
    by_name = {c["name"]: c for c in channels}
    # The channel GitHub posts commits into is the one with the incoming webhook.
    hooks = [h for h in (d.get(f"/guilds/{gid}/webhooks") or []) if h.get("type") == 1]
    if hooks and "🔨・commits" not in by_name:
        hook_channels = [c for c in channels if any(h["channel_id"] == c["id"] for h in hooks)]
        hook_channels.sort(key=lambda c: 0 if ("commit" in c["name"] or "github" in c["name"]) else 1)
        if hook_channels:
            c = hook_channels[0]
            d.call("PATCH", f"/channels/{c['id']}", {"name": "🔨・commits"})
            did(f"rename #{c['name']} (where the GitHub webhook posts) to 🔨・commits")
            c["name"] = "🔨・commits"
            by_name["🔨・commits"] = c
    for match, new_name in REUSE:
        if new_name in by_name:
            continue
        for c in channels:
            if match(c) and c["name"] not in [n for _, n in REUSE]:
                d.call("PATCH", f"/channels/{c['id']}", {"name": new_name})
                did(f"rename #{c['name']} to {new_name}")
                c["name"] = new_name
                by_name[new_name] = c
                break

    # categories and channels
    ids = {}
    types = {}
    for cat_name, cat_access, items in LAYOUT:
        cat = by_name.get(cat_name)
        if cat is None or cat["type"] != CATEGORY:
            cat = d.call("POST", f"/guilds/{gid}/channels", {"name": cat_name, "type": CATEGORY,
                                                             "permission_overwrites": overwrites(cat_access, gid, roles)})
            did(f"category {cat_name}")
            cat = cat or {"id": f"<{cat_name}>"}
        for name, ctype, access, topic in items:
            body = {"name": name, "type": ctype, "parent_id": cat["id"], "permission_overwrites": overwrites(access, gid, roles)}
            if topic and ctype in (TEXT, FORUM):
                body["topic"] = topic
            if ctype == FORUM:
                f = FORUM_SETUP[name]
                body["available_tags"] = [{"name": t, "emoji_name": e} for t, e in f["tags"]]
                body["default_forum_layout"] = f["layout"]
                if f["reaction"]:
                    body["default_reaction_emoji"] = {"emoji_name": f["reaction"]}
            current = by_name.get(name)
            if current is not None:
                ids[name] = current["id"]
                types[name] = current["type"]
                if current.get("parent_id") != cat["id"]:
                    # an existing channel (yours, renamed above): put it in place with this design's settings
                    patch = {k: v for k, v in body.items() if k not in ("type", "available_tags", "default_forum_layout")}
                    d.call("PATCH", f"/channels/{current['id']}", patch)
                    did(f"move {name} into {cat_name}")
                continue
            created = d.call("POST", f"/guilds/{gid}/channels", body)
            ids[name] = created["id"] if created else f"<{name}>"
            types[name] = ctype
            did(f"{'forum' if ctype == FORUM else 'voice' if ctype == VOICE else 'channel'} {name}")

    # Taz's categories first, in order; any old ones after them
    if apply:
        cats = [c for c in d.get(f"/guilds/{gid}/channels") if c["type"] == CATEGORY]
        ours = [n for n, _, _ in LAYOUT]
        cats.sort(key=lambda c: ours.index(c["name"]) if c["name"] in ours else len(ours) + c.get("position", 0))
        d.call("PATCH", f"/guilds/{gid}/channels", [{"id": c["id"], "position": i} for i, c in enumerate(cats)])
    did("put the categories in order")

    # server icon, Community (needed for announcements, the welcome screen and onboarding)
    icon_path = os.path.join(BRANDING, "taz-avatar-round-512.png")
    if os.path.exists(icon_path):
        with open(icon_path, "rb") as f:
            d.call("PATCH", f"/guilds/{gid}", {"icon": "data:image/png;base64," + base64.b64encode(f.read()).decode()})
        did("server icon")
    community = "COMMUNITY" in guild.get("features", [])
    if not community:
        try:
            d.call("PATCH", f"/guilds/{gid}", {
                "features": sorted(set(guild.get("features", [])) | {"COMMUNITY"}),
                "verification_level": max(guild.get("verification_level", 0), 1),
                "explicit_content_filter": 2,
                "rules_channel_id": ids["📜・rules"],
                "public_updates_channel_id": ids["📋・mod-log"],
                "description": "Hypixel SkyBlock mod: Galatea foraging, tree routes, dungeons, SkyBlock XP and more.",
            })
            community = True
            did("turn on Community (rules in #rules, Discord's updates in #mod-log)")
        except RuntimeError as e:
            print(f"  ! Community couldn't be turned on ({e}); announcements stay a normal channel.")
    if community and types.get("📣・announcements") == TEXT:
        try:
            d.call("PATCH", f"/channels/{ids['📣・announcements']}", {"type": ANNOUNCEMENT})
            did("make #announcements followable")
        except RuntimeError as e:
            print(f"  ! announcements: {e}")
    if community:
        try:
            d.call("PATCH", f"/guilds/{gid}/welcome-screen", {
                "enabled": True,
                "description": "One SkyBlock mod for Galatea foraging, tree routes, dungeons and more.",
                "welcome_channels": [
                    {"channel_id": ids["⬇️・download"], "description": "Install Taz", "emoji_name": "⬇️"},
                    {"channel_id": ids["✨・features"], "description": "See what it does", "emoji_name": "✨"},
                    {"channel_id": ids["💬・general"], "description": "Say hi", "emoji_name": "💬"},
                    {"channel_id": ids["🌲・tree-routes"], "description": "Grab a tree route", "emoji_name": "🌲"},
                    {"channel_id": ids["❓・help"], "description": "Get help", "emoji_name": "❓"},
                ],
            })
            did("welcome screen")
        except RuntimeError as e:
            print(f"  ! welcome screen: {e}")
        try:
            d.call("PUT", f"/guilds/{gid}/onboarding", {
                "enabled": True,
                "mode": 0,
                "default_channel_ids": [ids[n] for n in ("👋・welcome", "📜・rules", "📣・announcements", "⬇️・download", "✨・features",
                                                         "💬・general", "🎬・showcase", "📸・screenshots", "🌲・tree-routes", "❓・help",
                                                         "🐛・bug-reports", "💡・suggestions", "🔨・commits")],
                "prompts": [
                    {"id": snowflake(), "type": 0, "title": "What do you use Taz for?", "single_select": False, "required": False,
                     "in_onboarding": True, "options": [
                        {"id": snowflake(), "title": "Foraging on Galatea", "emoji_name": "🪓", "role_ids": [roles["Forager"]],
                         "channel_ids": [ids["🌲・tree-routes"]]},
                        {"id": snowflake(), "title": "Dungeons", "emoji_name": "⚔️", "role_ids": [roles["Dungeons"]], "channel_ids": []},
                        {"id": snowflake(), "title": "SkyBlock XP", "emoji_name": "✨", "role_ids": [roles["SkyBlock XP"]], "channel_ids": []},
                    ]},
                    {"id": snowflake(), "type": 0, "title": "Get pinged when Taz updates?", "single_select": True, "required": False,
                     "in_onboarding": True, "options": [
                        {"id": snowflake(), "title": "Yes, ping me", "emoji_name": "📣", "role_ids": [roles["Updates Ping"]],
                         "channel_ids": [ids["📣・announcements"]]},
                        {"id": snowflake(), "title": "No pings", "emoji_name": "🔕", "role_ids": [], "channel_ids": [ids["📣・announcements"]]},
                    ]},
                ],
            })
            did("onboarding questions (they hand out the interest and ping roles)")
        except RuntimeError as e:
            print(f"  ! onboarding: {e}")

    # posts
    links = {k.replace("-", "_"): f"<#{v}>" for k, v in {n.split("・")[-1]: i for n, i in ids.items()}.items()}
    links.update({"r_dev": f"<@&{roles['Taz Dev']}>", "r_mod": f"<@&{roles['Moderator']}>", "r_beta": f"<@&{roles['Beta Tester']}>",
                  "r_ping": f"<@&{roles['Updates Ping']}>", "r_forager": f"<@&{roles['Forager']}>",
                  "r_dungeons": f"<@&{roles['Dungeons']}>", "r_xp": f"<@&{roles['SkyBlock XP']}>"})
    for channel, post in POSTS.items():
        cid = ids[channel]
        if apply:
            recent = d.get(f"/channels/{cid}/messages?limit=20") or []
            if any(m.get("author", {}).get("id") == me["id"] for m in recent):
                continue  # posted on an earlier run
        body = {"embeds": fill_embed(post["embeds"], links), "allowed_mentions": {"parse": []}}
        files = None
        if post.get("file"):
            with open(os.path.join(BRANDING, post["file"]), "rb") as f:
                files = [(post["file"], f.read())]
        d.call("POST", f"/channels/{cid}/messages", body, files=files)
        did(f"post in {channel}")
    if apply:
        threads = d.get(f"/guilds/{gid}/threads/active") or {"threads": []}
        if not any(t.get("name") == ROUTE_GUIDE["name"] for t in threads.get("threads", [])):
            thread = d.call("POST", f"/channels/{ids['🌲・tree-routes']}/threads",
                            {"name": ROUTE_GUIDE["name"], "message": {"content": ROUTE_GUIDE["content"]}})
            if thread:
                try:
                    d.call("PATCH", f"/channels/{thread['id']}", {"flags": 1 << 1})  # pinned in the forum
                except RuntimeError:
                    pass
            did("pinned guide in #tree-routes")
    else:
        did("pinned guide in #tree-routes")

    print(f"\n{len(log)} change{'s' if len(log) != 1 else ''} {'made' if apply else 'to make'}.")
    if apply:
        print("Done! Old empty categories (like 'Text Channels') can be deleted by hand. You can kick the bot now.")


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as e:
        print(f"\nStopped: {e}")
        sys.exit(1)
