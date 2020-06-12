
comment on table link_previews_t is $c$

Both link_url_c and downloaded_from_url_c are part of the primary key
— otherwise an attacker's website A could hijack a widget from a normal
website W, like so:
  https://webs-a/widget pretends in a html tag that its oEmbed endpoint is
  E = https://webs-w/oembed?url=https://webs-w/widget  (note: webs-w, not -a)
and then later when someone tries to link to https://webs-w/widget,
whose oEmbed endpoint is E for real,
then, if lookng up by E only,
there'd already be a link_url_c = https//webs-a/widget associated with E,
i.e. pointing to the attacker's site.

But by including both link_url_c and downloaded_from_url_c in the primary key,
that cannot happen — when looking up https://webs-w/widget + E,
the attacker's entry wouldn't be found (because it's  https://webs-a/...).

$c$;

