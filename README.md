## THIS IS AN IN DEV PLUGIN!
For all infos and ideas message me directly on discord at @argonman 

Below you can find the roadmap of things i have to add, i will later publish a list of
- player races
- player classes
- class abilities

In total, i plan to add 8 races, 10 player classes, and 70 abilities (7 per class, each linked to z, x, c, v, b, n, m)

![Files i still have to work](https://i.ibb.co/ycfysWM8/Roadmap.png)

## Working commands as of now

/mana — requires mmorpg.admin.mana (op only)

/mana set <player> <value> — set a player's current mana to any value
/mana setmax <player> <value> — set a player's max mana
/mana setregen <player> <value> — set mana regen per tick
/mana fill <player> — instantly fill mana to max
/mana get <player> — print full mana breakdown to chat


/mmorpg — requires mmorpg.admin (op only)

/mmorpg reload — hot-reload config.yml without restarting
/mmorpg debug <player> — dump a player's full PlayerData to chat


/cast — no permission required (any player)

/cast demo — fires the AOE Burst demo ability (costs 30 mana, 20 stamina, 25% health, 8s cooldown)
/cast help — shows cost/cooldown info for demo abilities

/mmorpg reload — hot-reload config without restart
/mmorpg debug <player> — dump all PlayerData fields to chat

