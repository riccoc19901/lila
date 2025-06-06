import { attributesModule, classModule, init, VNode } from 'snabbdom';
import menuHover from 'lib/menuHover';
import StormCtrl from './ctrl';
import type { StormOpts } from './interfaces';

const patch = init([classModule, attributesModule]);

import view from './view/main';

export function initModule(opts: StormOpts): void {
  const element = document.querySelector('.storm-app') as HTMLElement;

  let vnode: VNode;

  function redraw() {
    vnode = patch(vnode, view(ctrl));
  }

  const ctrl = new StormCtrl(opts, redraw);

  const blueprint = view(ctrl);
  element.innerHTML = '';
  vnode = patch(element, blueprint);

  menuHover();
  $('script').remove();
}
