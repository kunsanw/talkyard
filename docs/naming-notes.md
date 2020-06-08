Talkyard Naming Notes
==========================

### Categories

Each forum:

Root category —> Main categories —> Sub categories —> (Sub sub categories? or not?)

A main category is called "Main", not "Top", because "top" could be
incorrectly interpreted as "popular".

Root categories are Talkyard internal things — end users never see them;
they never see the phrase "Root category".



### Database tables, columns etc

Table names ends with `_t`, e.g. `links_t`.
Column names end with `_c`, e.g. `site_c`.

Otherwise it'd take long to find all occurrences of e.g.
the links table — example: if you search for "links"
you find 99% off-topic matches, but "links_t" gives you close to 100%
on-topic search results. Also, now you can just type: `link_url_c` without
explaining that it's a database column — the other Ty devs willl know,
just by looking at `_c`. And can find it instantly in the database docs.

"Participant" is abbreviated with "pp", or "..._by". E.g. `links_t.to_pp_id_c` means
a link to the tparticipant with the id in the `to_pp_id_c` column.
Or e.g. `written_by_c`.

Constraints and indexes:

 - Primary keys: `tablename_p` or `tablename_p_column1_col2_etc`.
 - Foreign keys: `tablename_r_othertable` or `table_col1_col2_r_otherable`
   ('r' means "references").
 - Check constraints: `tablename_c_columnname` e.g. `linkpreviews_c_linkurl_len` — checks the
   length of the `link_previews_t.link_url_c` column.
 - Unique indexes: `tablename_u_col1_col2_etc`.
 - Other indexes: `tablename_i_col1_col2_etc`.

Don't include the `site_id_c` column in these names — it's always there, not
interesting. Instead, in the few cases where the site is _not_ included,
add `_g` instead, for "global" index: `tablename_i_g_col1` means `col1` across
all sites.


When adding a foreign key, always include a comment on the line above
about which index indexes that foreign key. Example:

```
create table links_t(
  ...
  to_post_id_c int,
  ...

  -- fk index: links_i_topost
  constraint links_topost_r_posts foreign key (site_c, to_post_id_c)
      references posts3 (site_id, unique_post_id),
  ...
);

...

create index links_i_topost on links_t (site_c, to_post_id_c);
```

(Need not include "site" in the index name — there's always a site id in all indexes (almost).)


(Old table names look like `sometable3` for historical reasons,
but nowadays it's `sometable_t` instead.
And old columns: `site_id` or `post_id` but now it's `site_c` and `post_c` instead
— it's obvious that the columns are ids?)



### Cookies

Names like `tyCo...` so you can: `grep -r tyCo ./` and find all cookies.
(Right now it's `dwCo...`, should change to `tyCo`. RENAME )
